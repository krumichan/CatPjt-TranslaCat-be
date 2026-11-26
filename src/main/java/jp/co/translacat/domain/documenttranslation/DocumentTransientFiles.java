package jp.co.translacat.domain.documenttranslation;

import com.sun.security.auth.module.NTSystem;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** 한 런타임이 소유하는 문서 작업 파일과 열려 있는 다운로드의 수명을 관리한다. */
final class DocumentTransientFiles implements AutoCloseable {
    private static final String LOCK_FILE = "runtime.lock";
    private static final String JOB_PREFIX = "job-";
    private static final String SOURCE = "source";
    private static final String RESULT = "result";
    private static final Pattern OFFICE_SCRATCH = Pattern.compile("\\.office-output-[0-9]+\\.zip");
    private static final Pattern SOURCE_SHA256 = Pattern.compile("[a-f0-9]{64}");
    private static final Set<PosixFilePermission> PRIVATE_MODE = PosixFilePermissions.fromString("rwx------");

    private final Path root;
    private final FileChannel lockChannel;
    private final FileLock runtimeLock;
    private final Map<UUID, Workspace> workspaces = new HashMap<>();
    private boolean closing;

    /**
     * 전용 작업 루트를 열고 배타 잠금을 확보한 다음 이전 프로세스의 파일을 정리한다.
     *
     * @param configuredRoot 루트의 실제 부모 디렉터리가 존재하는 전용 경로
     * @throws IOException 루트 권한, 런타임 잠금, 시작 정리를 확인할 수 없을 때
     */
    DocumentTransientFiles(Path configuredRoot) throws IOException {
        root = prepareRoot(configuredRoot);
        Path lockPath = root.resolve(LOCK_FILE);
        if (attributes(lockPath) != null) {
            requireFile(lockPath);
        }

        // 채널을 보유한 동안 다른 런타임은 정리하거나 새 작업을 시작할 수 없다.
        FileChannel channel = FileChannel.open(
                lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        try {
            try {
                runtimeLock = channel.tryLock();
            } catch (OverlappingFileLockException busy) {
                throw new IOException("Document workspace is already owned.", busy);
            }
            if (runtimeLock == null) {
                throw new IOException("Document workspace is already owned.");
            }
            requireFile(lockPath);
            recover();
        } catch (IOException | RuntimeException failure) {
            try {
                channel.close();
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        lockChannel = channel;
    }

    // 같은 작업 ID의 이전 파일을 재사용하지 않고 새 디렉터리를 등록한다.
    synchronized Path create(UUID jobId) throws IOException {
        requireOpen();
        Objects.requireNonNull(jobId);
        if (workspaces.containsKey(jobId)) {
            throw new FileAlreadyExistsException("Document workspace is already registered.");
        }
        Path directory = root.resolve(JOB_PREFIX + jobId);
        Files.createDirectory(directory);
        workspaces.put(jobId, new Workspace(directory));
        return directory;
    }

    // 입력 파일에 사용할 고정 경로를 반환한다. 실제 입출력 동안에는 사용권을 유지한다.
    synchronized Path source(UUID jobId) throws IOException {
        return availablePath(workspace(jobId), SOURCE);
    }

    // 결과 파일에 사용할 고정 경로를 반환한다. 실제 입출력 동안에는 사용권을 유지한다.
    synchronized Path result(UUID jobId) throws IOException {
        return availablePath(workspace(jobId), RESULT);
    }

    // 입력을 다시 쓰지 않는 단계에서 원본을 먼저 정리하며, 이미 지워진 원본은 허용한다.
    synchronized void deleteSource(UUID jobId) throws IOException {
        Workspace workspace = workspaces.get(jobId);
        if (workspace != null) {
            clearSourceIdentity(workspace);
            Path source = availablePath(workspace, SOURCE);
            Files.deleteIfExists(source);
        }
    }

    // 검증된 원본 지문을 작업 메모리에 한 번만 넣고, 삭제한 원본을 다시 식별하지 않는다.
    synchronized void identifySource(UUID jobId, String digest) throws IOException {
        Workspace workspace = workspace(jobId);
        if (workspace.sourceErased || digest == null || !SOURCE_SHA256.matcher(digest).matches()) {
            throw new IOException("Document source identity is unavailable.");
        }
        byte[] fingerprint = HexFormat.of().parseHex(digest);
        if (workspace.sourceFingerprint == null) {
            workspace.sourceFingerprint = fingerprint;
        } else if (!MessageDigest.isEqual(workspace.sourceFingerprint, fingerprint)) {
            throw new IOException("Document source identity changed.");
        }
    }

    // 재요청의 원본은 아직 사용 가능한 작업의 메모리 지문으로만 확인한다.
    synchronized boolean matchesSource(UUID jobId, String digest) {
        Workspace workspace = workspaces.get(jobId);
        return !closing && runtimeLock.isValid() && workspace != null && !workspace.deletePending
                && workspace.sourceFingerprint != null && digest != null && SOURCE_SHA256.matcher(digest).matches()
                && MessageDigest.isEqual(workspace.sourceFingerprint, HexFormat.of().parseHex(digest));
    }

    // 삭제 여부가 불확실해도 재요청의 일치 근거가 다시 사용되지 않게 한다.
    private static void clearSourceIdentity(Workspace workspace) {
        if (workspace.sourceFingerprint != null) {
            Arrays.fill(workspace.sourceFingerprint, (byte) 0);
            workspace.sourceFingerprint = null;
        }
        workspace.sourceErased = true;
    }

    // DB 상태와 관계없이 보관 기한을 등록한다. 삭제가 시작된 작업은 되살리지 않는다.
    synchronized void retainUntil(UUID jobId, Instant expiresAt) throws IOException {
        workspace(jobId).expiresAt = Objects.requireNonNull(expiresAt);
    }

    // 원본이나 결과를 쓰고 읽는 동안 청소를 늦추는 닫을 수 있는 사용권을 반환한다.
    synchronized Lease use(UUID jobId) throws IOException {
        Workspace workspace = workspace(jobId);
        workspace.borrowers++;
        AtomicBoolean released = new AtomicBoolean();
        return () -> {
            if (released.compareAndSet(false, true)) {
                try {
                    release(jobId, workspace, false);
                } catch (IOException deferred) {
                    // 정리 실패 시 메모리 항목을 남겨 다음 reap 또는 종료에서 다시 확인한다.
                }
            }
        };
    }

    // 새 접근을 먼저 차단하고, 열린 파일이 있는 동안에는 실제 삭제를 연기한다.
    synchronized void discard(UUID jobId) throws IOException {
        Objects.requireNonNull(jobId);
        Workspace workspace = workspaces.get(jobId);
        if (workspace != null) {
            workspace.deletePending = true;
            clearSourceIdentity(workspace);
        }
        requireOwnership();
        if (workspace == null) {
            removeWorkspace(root.resolve(JOB_PREFIX + jobId));
            return;
        }
        if (workspace.borrowers == 0) {
            discardIdle(jobId, workspace);
        }
    }

    // 계정 삭제에서 열린 사용권이나 남아 있는 작업 경로를 삭제 완료로 취급하지 않는다.
    synchronized boolean hasWorkspace(UUID jobId) throws IOException {
        requireOwnership();
        Objects.requireNonNull(jobId);
        Workspace workspace = workspaces.get(jobId);
        if (workspace != null && workspace.borrowers > 0) {
            return true;
        }
        Path directory = root.resolve(JOB_PREFIX + jobId);
        if (attributes(directory) == null) {
            return false;
        }
        requireWorkspaceDirectory(directory);
        return true;
    }

    // 아직 한 번도 청구되지 않은 정상 결과 파일이 현재 존재하는지 확인한다.
    synchronized boolean hasResult(UUID jobId) throws IOException {
        Workspace workspace = workspaces.get(jobId);
        return !closing && workspace != null && !workspace.deletePending && !workspace.resultClaimed
                && attributes(availablePath(workspace, RESULT)) != null;
    }

    // 하나의 응답만 결과를 열 수 있으며, 닫힌 응답의 소유 디렉터리를 정리한다.
    synchronized InputStream openResult(UUID jobId) throws IOException {
        Workspace workspace = workspace(jobId);
        if (workspace.resultClaimed) {
            throw new NoSuchFileException("Document result is unavailable.");
        }

        // 성공적으로 열기 전에는 사용권을 부여하지 않으며, 경로와 열기 모두 링크를 거부한다.
        Path result = availablePath(workspace, RESULT);
        InputStream input = Files.newInputStream(result, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        workspace.resultClaimed = true;
        workspace.borrowers++;
        return discardOnClose(input, () -> release(jobId, workspace, true));
    }

    // 원본 스트림의 종료가 실패해도 결과의 사용권을 정확히 한 번 반환한다.
    static InputStream discardOnClose(InputStream input, ResultRelease release) {
        return new FilterInputStream(input) {
            private boolean closed;

            /** 파일 핸들이 닫힌 뒤 삭제를 요청하고, 실패하면 이후 청소가 이어받는다. */
            @Override
            public synchronized void close() throws IOException {
                if (closed) {
                    return;
                }
                closed = true;
                // 파일 종료가 먼저 실패하면 그 예외를 유지하고 정리 예외는 억제 예외로 남긴다.
                try (release) {
                    super.close();
                }
            }
        };
    }

    // 기한이 지난 파일과 앞서 삭제에 실패한 파일을 DB 조회 없이 다시 확인한다.
    synchronized void reap(Instant now) throws IOException {
        requireOpen();
        IOException failure = null;
        for (UUID jobId : new ArrayList<>(workspaces.keySet())) {
            Workspace workspace = workspaces.get(jobId);
            if (workspace.deletePending || workspace.expiresAt != null && !now.isBefore(workspace.expiresAt)) {
                try {
                    discard(jobId);
                } catch (IOException unavailable) {
                    failure = appendFailure(failure, unavailable);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** 새 접근을 닫고 사용하지 않는 파일을 정리한 뒤, 사용권이 모두 반환되면 잠금을 해제한다. */
    @Override
    public synchronized void close() throws IOException {
        if (!lockChannel.isOpen()) {
            return;
        }
        closing = true;
        IOException failure = null;
        for (UUID jobId : new ArrayList<>(workspaces.keySet())) {
            try {
                discard(jobId);
            } catch (IOException unavailable) {
                failure = appendFailure(failure, unavailable);
            }
        }

        // 열려 있는 응답을 다른 런타임의 시작 정리와 겹치게 하지 않는다.
        try {
            closeLockIfIdle();
        } catch (IOException unavailable) {
            failure = appendFailure(failure, unavailable);
        }
        if (failure != null) {
            throw failure;
        }
    }

    // 한 사용권의 종료와 마지막 파일 정리를 다른 사용자와 직렬화한다.
    private synchronized void release(UUID jobId, Workspace workspace, boolean discardResult) throws IOException {
        workspace.borrowers--;
        IOException failure = null;
        if (discardResult || workspace.deletePending) {
            try {
                discard(jobId);
            } catch (IOException unavailable) {
                failure = unavailable;
            }
        }
        try {
            closeLockIfIdle();
        } catch (IOException unavailable) {
            failure = appendFailure(failure, unavailable);
        }
        if (failure != null) {
            throw failure;
        }
    }

    // 삭제가 성공했을 때만 작업 항목을 제거해 실패 항목을 재시도 대상으로 남긴다.
    private void discardIdle(UUID jobId, Workspace workspace) throws IOException {
        removeWorkspace(workspace.directory);
        workspaces.remove(jobId);
    }

    // 종료가 요청된 런타임의 마지막 사용권까지 반환되었을 때만 배타 잠금을 놓는다.
    private void closeLockIfIdle() throws IOException {
        if (closing && lockChannel.isOpen() && workspaces.values().stream().noneMatch(item -> item.borrowers > 0)) {
            lockChannel.close();
        }
    }

    // 삭제를 기다리는 작업과 종료된 런타임에는 새 작업 접근을 허용하지 않는다.
    private Workspace workspace(UUID jobId) throws IOException {
        requireOpen();
        Workspace workspace = workspaces.get(jobId);
        if (workspace == null || workspace.deletePending) {
            throw new NoSuchFileException("Document workspace is unavailable.");
        }
        return workspace;
    }

    // 잠금과 루트 상태가 맞는 동안에만 작업 디렉터리를 만든다.
    private void requireOpen() throws IOException {
        if (closing) {
            throw new IOException("Document workspace is closed.");
        }
        requireOwnership();
    }

    // 닫힌 런타임은 같은 위치를 새로 소유한 작업에 접근할 수 없다.
    private void requireOwnership() throws IOException {
        if (!runtimeLock.isValid()) {
            throw new IOException("Document workspace is closed.");
        }
        requireDirectory(root);
    }

    // 기존 파일이 연결이나 다른 종류로 바뀌었으면 고정 이름이라도 사용하지 않는다.
    private static Path availablePath(Workspace workspace, String name) throws IOException {
        requireWorkspaceDirectory(workspace.directory);
        Path path = workspace.directory.resolve(name);
        if (attributes(path) != null) {
            requireFile(path);
        }
        return path;
    }

    // 런타임이 독점한 루트의 직계 작업 디렉터리만 시작 시 정리한다.
    private void recover() throws IOException {
        try (DirectoryStream<Path> children = Files.newDirectoryStream(root, JOB_PREFIX + "*")) {
            for (Path child : children) {
                if (isJobDirectoryName(child.getFileName().toString())
                        && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    removeWorkspace(child);
                }
            }
        }
    }

    // UUID 파서의 관대한 형식도 거부하고 런타임이 만드는 소문자 이름만 사용한다.
    private static boolean isJobDirectoryName(String name) {
        if (!name.startsWith(JOB_PREFIX)) {
            return false;
        }
        String id = name.substring(JOB_PREFIX.length());
        try {
            return UUID.fromString(id).toString().equals(id);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    // 파일과 패키징 임시 파일만 수거하고, 다른 디렉터리나 링크는 건드리지 않는다.
    private static void removeWorkspace(Path directory) throws IOException {
        if (attributes(directory) == null) {
            return;
        }
        requireWorkspaceDirectory(directory);

        // 모든 항목을 확인한 다음 삭제하여 예상하지 않은 트리에 손대지 않는다.
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                String name = child.getFileName().toString();
                if (!name.equals(SOURCE) && !name.equals(RESULT) && !OFFICE_SCRATCH.matcher(name).matches()) {
                    throw new IOException("Unexpected document workspace content.");
                }
                requireFile(child);
                files.add(child);
            }
        }
        for (Path file : files) {
            Files.deleteIfExists(file);
        }
        Files.deleteIfExists(directory);
    }

    // 물리적으로 같은 경로를 제한하고, 해당 호스트의 소유자 전용 권한을 확인한다.
    private static Path prepareRoot(Path configuredRoot) throws IOException {
        Path root = configuredRoot.toAbsolutePath().normalize();
        Path parent = root.getParent();
        if (parent == null) {
            throw new IOException("Document workspace requires an existing parent.");
        }
        requireDirectory(parent);
        var storage = Files.getFileStore(parent);
        boolean posix = storage.supportsFileAttributeView(PosixFileAttributeView.class);
        if (!posix && (!System.getProperty("os.name").startsWith("Windows")
                || !storage.supportsFileAttributeView(AclFileAttributeView.class))) {
            throw new IOException("Document workspace does not support private permissions.");
        }
        var principals = root.getFileSystem().getUserPrincipalLookupService();
        UserPrincipal owner = principals.lookupPrincipalByName(
                posix ? System.getProperty("user.name") : windowsAccount());
        UserPrincipal system = posix ? null : principals.lookupPrincipalByName("NT AUTHORITY\\SYSTEM");
        FileAttribute<?> permissions = posix
                ? PosixFilePermissions.asFileAttribute(PRIVATE_MODE) : ownerAcl(owner, system);
        boolean created = false;
        try {
            Files.createDirectory(root, permissions);
            created = true;
        } catch (FileAlreadyExistsException existing) {
            // 기존 루트의 소유권을 바꾸지 않고 같은 조건을 증명한다.
        }

        // 권한을 검증할 수 없는 저장소에서는 문서를 받지 않는다.
        requireDirectory(root);
        if (posix) {
            if (!Files.getOwner(root, LinkOption.NOFOLLOW_LINKS).equals(owner)
                    || !Files.getPosixFilePermissions(root, LinkOption.NOFOLLOW_LINKS).equals(PRIVATE_MODE)) {
                throw new IOException("Document workspace must have owner-only permissions.");
            }
        } else {
            verifyWindowsRoot(root, owner, system, created);
        }
        return root;
    }

    // Windows는 JVM 속성의 짧은 사용자 이름 대신 현재 토큰의 도메인과 계정을 사용한다.
    private static String windowsAccount() throws IOException {
        var token = new NTSystem();
        String name = token.getName();
        String domain = token.getDomain();
        if (name == null || name.isBlank() || domain == null || domain.isBlank()) {
            throw new IOException("Document workspace requires a Windows runtime identity.");
        }
        return domain + "\\" + name;
    }

    // OS 기본 소유자는 새 루트 또는 기존 잠금과 정확한 ACL이 있는 루트에서만 확인한다.
    private static void verifyWindowsRoot(Path root, UserPrincipal owner, UserPrincipal system, boolean created)
            throws IOException {
        AclFileAttributeView acl = Files.getFileAttributeView(root, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl == null) {
            throw new IOException("Document workspace ACL is unavailable.");
        }
        UserPrincipal actualOwner = acl.getOwner();
        if (!actualOwner.equals(owner)) {
            if (!isTrustedWindowsDefault(root, actualOwner, system)) {
                throw new IOException("Document workspace owner did not match.");
            }

            if (created) {
                // 허용된 OS 기본 소유자를 계정 소유로 바꿀 수 있을 때만 변경한다.
                try {
                    acl.setOwner(owner);
                } catch (AccessDeniedException defaultOwner) {
                    // 검증된 전용 DACL을 요구하고, 일반 권한으로 수정할 수 없는 OS 소유자는 유지한다.
                }
            } else {
                if (!isPrivateWindowsAcl(acl, owner, system)) {
                    throw new IOException("Existing document workspace ACL did not match.");
                }
                requireFile(root.resolve(LOCK_FILE));
            }
        }

        // 런타임 계정과 SYSTEM 외의 권한을 확인하면 새 루트에서만 DACL을 교정한다.
        if (created && !isPrivateWindowsAcl(acl, owner, system)) {
            acl.setAcl(ownerAcl(owner, system).value());
        }
        actualOwner = acl.getOwner();
        if (!isPrivateWindowsAcl(acl, owner, system)
                || !actualOwner.equals(owner) && !isTrustedWindowsDefault(root, actualOwner, system)) {
            throw new IOException("Document workspace must have a private Windows ACL.");
        }
    }

    // 특정 OS 기본 계정과 관리자 그룹만 확인하고, 임의의 그룹 소유권을 받아들이지 않는다.
    private static boolean isTrustedWindowsDefault(Path root, UserPrincipal actualOwner, UserPrincipal system)
            throws IOException {
        if (actualOwner.equals(system)) {
            return true;
        }
        var administrators = root.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByGroupName("BUILTIN\\Administrators");
        return actualOwner.equals(administrators);
    }

    // 실제 DACL에 필요한 지정 계정의 허용 규칙만 존재하는지 검증한다.
    private static boolean isPrivateWindowsAcl(AclFileAttributeView acl, UserPrincipal owner, UserPrincipal system)
            throws IOException {
        Set<UserPrincipal> allowed = new HashSet<>(List.of(owner, system));
        Set<UserPrincipal> verified = new HashSet<>();
        for (AclEntry entry : acl.getAcl()) {
            if (entry.type() != AclEntryType.ALLOW || !allowed.contains(entry.principal())) {
                return false;
            }
            if (!entry.flags().contains(AclEntryFlag.INHERIT_ONLY)) {
                verified.add(entry.principal());
            }
        }
        return verified.equals(allowed);
    }

    // Windows 작업 루트의 자식에도 런타임 계정과 SYSTEM의 권한만 상속한다.
    private static FileAttribute<List<AclEntry>> ownerAcl(UserPrincipal owner, UserPrincipal system) {
        List<AclEntry> acl = List.of(owner, system).stream().distinct().map(principal -> AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(principal)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT)
                .build()).toList();
        return new FileAttribute<>() {
            @Override
            public String name() {
                return "acl:acl";
            }

            @Override
            public List<AclEntry> value() {
                return acl;
            }
        };
    }

    // 권한 오류를 파일 없음으로 오인하지 않는 no-follow 속성 조회를 사용한다.
    private static BasicFileAttributes attributes(Path path) throws IOException {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException missing) {
            return null;
        }
    }

    // 심볼릭 링크 및 실제 위치가 다른 디렉터리 연결을 거부한다.
    private static Path requireDirectory(Path path) throws IOException {
        BasicFileAttributes info = attributes(path);
        if (info == null || !info.isDirectory()) {
            throw new IOException("A real document workspace directory is required.");
        }
        Path real = path.toRealPath();
        if (!real.equals(path)) {
            throw new IOException("A real document workspace directory is required.");
        }
        return real;
    }

    // 대소문자를 구분하지 않는 호스트에서도 실제 작업 디렉터리 이름을 확인한다.
    private static void requireWorkspaceDirectory(Path path) throws IOException {
        Path real = requireDirectory(path);
        if (!real.getFileName().toString().equals(path.getFileName().toString())) {
            throw new IOException("Unexpected document workspace name.");
        }
    }

    // 파일만 허용하고 파일 경로에서 다른 위치로 연결되지 않는지 확인한다.
    private static void requireFile(Path path) throws IOException {
        BasicFileAttributes info = attributes(path);
        if (info == null || !info.isRegularFile()) {
            throw new IOException("A regular document workspace file is required.");
        }
        Path real = path.toRealPath();
        if (!real.equals(path) || !real.getFileName().toString().equals(path.getFileName().toString())) {
            throw new IOException("A regular document workspace file is required.");
        }
    }

    // 첫 실패를 보존하면서 이후 작업도 정리할 수 있도록 추가 실패를 묶는다.
    private static IOException appendFailure(IOException first, IOException next) {
        if (first == null) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }

    /** 작업자가 작업 파일을 빌린 동안 삭제를 유예하며 닫을 때 사용권만 반환한다. */
    interface Lease extends AutoCloseable {
        @Override
        void close();
    }

    /** 다운로드 응답의 결과 사용권을 반환하고 실제 정리 실패를 전달한다. */
    @FunctionalInterface
    interface ResultRelease extends AutoCloseable {
        @Override
        void close() throws IOException;
    }

    /** 런타임 잠금 아래 생성한 디렉터리의 현재 사용·만료 상태를 저장한다. */
    private static final class Workspace {
        private final Path directory;
        private Instant expiresAt;
        private byte[] sourceFingerprint;
        private int borrowers;
        private boolean deletePending;
        private boolean resultClaimed;
        private boolean sourceErased;

        private Workspace(Path directory) {
            this.directory = directory;
        }
    }
}
