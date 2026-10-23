package jp.co.translacat.infrastructure.storage.local;

import jp.co.translacat.domain.user.profile.storage.model.ImageStorageUpload;
import jp.co.translacat.domain.user.profile.storage.port.ImageStoragePort;
import jp.co.translacat.novel.application.NovelAudioObjectStore;
import jp.co.translacat.novel.domain.NovelProblem;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.storage.config.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
@ConditionalOnProperty(
        prefix = "translacat.storage",
        name = "type",
        havingValue = "local",
        matchIfMissing = true
)
public class LocalImageStorageAdapter implements ImageStoragePort, NovelAudioObjectStore {

    private final Path rootPath;
    private final Path privateAudioRoot;
    private final String publicBaseUrl;

    public LocalImageStorageAdapter(StorageProperties properties) {
        this.rootPath = Path.of(
                properties.getLocal().getRootPath()
        ).toAbsolutePath().normalize();
        this.privateAudioRoot = rootPath.resolveSibling(rootPath.getFileName() + "-private-audio");

        this.publicBaseUrl = stripTrailingSlash(
                properties.getLocal().getPublicBaseUrl()
        );
    }

    @Override
    public void store(ImageStorageUpload upload) {
        Path targetPath = resolveSafePath(upload.objectKey());

        try {
            Files.createDirectories(targetPath.getParent());
            Files.write(
                    targetPath,
                    upload.bytes(),
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            throw new BusinessException(
                    "이미지 저장에 실패했습니다.",
                    e
            );
        }
    }

    @Override
    public void delete(String objectKey) {
        Path targetPath = resolveSafePath(objectKey);

        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException e) {
            throw new BusinessException(
                    "이미지 삭제에 실패했습니다.",
                    e
            );
        }
    }

    @Override
    public boolean supportsAccountDeletion() { return true; }

    @Override
    public boolean deleteUserProfileObjects(long userId) {
        if (userId <= 0) throw new IllegalArgumentException("Positive account ID required");
        Path prefix = resolveSafePath("user-profile/" + userId);
        if (!Files.exists(prefix)) return true;
        if (Files.isSymbolicLink(prefix) || Files.isSymbolicLink(prefix.getParent())) throw new IllegalStateException("Profile cleanup path cannot be a symbolic link");
        try {
            // 재귀 디렉터리 삭제 대신 검증한 계정 prefix 안의 leaf만 최대 100개 정리한다.
            try (var paths = Files.walk(prefix)) {
                for (Path file : paths.filter(path -> Files.isRegularFile(path) || Files.isSymbolicLink(path)).limit(100).toList()) {
                    if (!file.normalize().startsWith(prefix)) throw new IllegalStateException("Profile cleanup path escaped prefix");
                    Files.deleteIfExists(file);
                }
            }
            try (var paths = Files.walk(prefix)) {
                return paths.noneMatch(path -> Files.isRegularFile(path) || Files.isSymbolicLink(path));
            }
        } catch (IOException failure) {
            throw new BusinessException("프로필 이미지 정리에 실패했습니다.", "PROFILE_CLEANUP_UNAVAILABLE");
        }
    }

    @Override
    public String resolvePublicUrl(String objectKey) {
        return publicBaseUrl + "/" + objectKey;
    }

    @Override
    public void put(String objectKey, Path file, String contentType, long bytes, String sha256) {
        Path target = audioPath(objectKey);
        try {
            Files.createDirectories(target.getParent());
            // 로컬 검증에서도 공개 이미지 파일 경로와 음성 파일을 분리한다.
            Files.copy(file, target);
        } catch (java.nio.file.FileAlreadyExistsException existing) {
            if (!sha256.equals(checksum(target))) throw new NovelProblem("AUDIO_OBJECT_CONFLICT", 409);
        } catch (IOException failure) {
            throw new NovelProblem("AUDIO_STORAGE_UNAVAILABLE", 503, true, 1000);
        }
    }

    @Override
    public ObjectInfo head(String objectKey) {
        Path target = audioPath(objectKey);
        if (!Files.isRegularFile(target)) throw new NovelAudioObjectStore.Missing();
        try {
            return new ObjectInfo(Files.size(target), checksum(target), "audio/wav");
        } catch (IOException failure) {
            throw new NovelProblem("AUDIO_STORAGE_UNAVAILABLE", 503, true, 1000);
        }
    }

    @Override
    public void deleteAudio(String objectKey) {
        // 음성은 공개 이미지 루트와 분리되어 있으므로 기존 이미지 delete로 대신 처리하지 않는다.
        Path target = audioPath(objectKey);
        try { Files.deleteIfExists(target); }
        catch (IOException failure) { throw new NovelProblem("AUDIO_STORAGE_UNAVAILABLE", 503, true, 1000); }
    }

    @Override
    public ObjectData read(String objectKey, int maxBytes) {
        Path target = audioPath(objectKey);
        if (!Files.isRegularFile(target)) throw new NovelAudioObjectStore.Missing();
        try (var stream = Files.newInputStream(target)) {
            byte[] data = stream.readNBytes(maxBytes + 1);
            if (data.length > maxBytes) throw new NovelProblem("AUDIO_OBJECT_TOO_LARGE", 502);
            return new ObjectData(data, "audio/wav");
        } catch (IOException failure) {
            throw new NovelProblem("AUDIO_STORAGE_UNAVAILABLE", 503, true, 1000);
        }
    }

    private Path audioPath(String key) {
        if (key == null || !key.matches("novel/audio/v1/[0-9a-f]{64}/[0-9]+\\.wav")) {
            throw new NovelProblem("AUDIO_OBJECT_KEY_INVALID", 400);
        }
        Path target = privateAudioRoot.resolve(key).normalize();
        if (!target.startsWith(privateAudioRoot)) throw new NovelProblem("AUDIO_OBJECT_KEY_INVALID", 400);
        return target;
    }

    private static String checksum(Path file) {
        try (var input = Files.newInputStream(file)) {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] block = new byte[8192];
            for (int read; (read = input.read(block)) != -1;) digest.update(block, 0, read);
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (Exception failure) {
            throw new NovelProblem("AUDIO_STORAGE_UNAVAILABLE", 503, true, 1000);
        }
    }

    public Path resolveSafePath(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(
                    "이미지 object key는 필수입니다.",
                    "PROFILE_IMAGE_OBJECT_KEY_REQUIRED"
            );
        }

        Path resolved = rootPath.resolve(objectKey).normalize();

        if (!resolved.startsWith(rootPath)) {
            throw new BusinessException(
                    "잘못된 이미지 경로입니다.",
                    "PROFILE_IMAGE_INVALID_OBJECT_KEY"
            );
        }

        return resolved;
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "STORAGE_LOCAL_PUBLIC_BASE_URL 설정이 필요합니다."
            );
        }

        return value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }
}
