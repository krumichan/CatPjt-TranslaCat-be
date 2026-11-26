package jp.co.translacat.domain.documenttranslation;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.BooleanSupplier;

/** 문서 객체를 비공개로 저장하고 스트림으로 조회하는 포트다. */
interface DocumentFiles {

    // 작업 전용 키에 파일을 완전히 저장하고 기록한 길이를 확인한다.
    void put(String key, Path source) throws IOException;

    // 저장소에 도달하기 전 대기 중 삭제로 종료된 쓰기인지 재확인한다.
    default void put(String key, Path source, BooleanSupplier stillWriting) throws IOException {
        if (!stillWriting.getAsBoolean()) {
            throw new IOException("Document object write was closed");
        }
        put(key, source);
    }

    // 객체 소유권을 검증한 호출자에게 읽기 스트림을 넘긴다.
    InputStream open(String key) throws IOException;

    // 키가 없어도 성공하는 만료 정리 연산이다.
    void delete(String key) throws IOException;

    // 키가 실제로 존재하면 유일한 PUT이 저장소 바깥에서 완료된 사실을 확인할 수 있다.
    boolean exists(String key) throws IOException;

    // 영속 PUT의 긍정 응답 또는 저장소가 제공하는 쓰기 격리로만 완료를 판정한다.
    default void reconcile(String key, Runnable settled) throws IOException {
        if (exists(key)) {
            settled.run();
        }
    }

    // 기록된 길이까지만 읽고 해시가 일치하는 완전한 사본을 제공한다.
    default void copyVerified(String key, Path destination, long expectedBytes, String expectedHash)
            throws IOException {
        MessageDigest digest = sha256();
        long total = 0;
        try (var input = open(key); var output = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > expectedBytes) {
                    throw new IOException("Stored document exceeds its recorded size");
                }
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
        }
        if (total != expectedBytes || !HexFormat.of().formatHex(digest.digest()).equals(expectedHash)) {
            throw new IOException("Stored document does not match its record");
        }
    }

    // 파일과 요청 서명에 쓰는 필수 JDK 알고리즘을 가져온다.
    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException("SHA-256 is not available");
        }
    }

    /** 격리된 개발 및 단일 호스트 시험 디렉터리에 파일을 보관한다. */
    record Local(Path root) implements DocumentFiles {
        @Override
        public void put(String key, Path source) throws IOException {
            put(key, source, () -> true);
        }

        @Override
        public void put(String key, Path source, BooleanSupplier stillWriting) throws IOException {
            if (!stillWriting.getAsBoolean()) {
                throw new IOException("Document object write was closed");
            }
            Path target = resolve(key);
            boolean closed = false;
            // 쓰기와 끊긴 쓰기 복구는 같은 파일 잠금을 사용한다. 복구 이후 늦게 열린 쓰기는 상태 검사에서 중지된다.
            try (var guard = guard(target); var lock = tryLock(guard)) {
                if (lock == null) {
                    throw new UnconfirmedWrite();
                }
                closed = !stillWriting.getAsBoolean();
                if (closed) {
                    throw new IOException("Document object write was closed");
                }

                Path staged = staged(target);
                Files.createFile(staged);
                try {
                    Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
                } finally {
                    Files.deleteIfExists(staged);
                }
            } finally {
                // 정리 이후 접근한 오래된 호출은 새로 연 빈 잠금 파일도 보관하지 않는다.
                if (closed) {
                    Files.deleteIfExists(guardPath(target));
                }
            }
        }

        @Override
        public void reconcile(String key, Runnable settled) throws IOException {
            // 로컬 파일 잠금이 해제되면 이전 프로세스의 쓰기가 더 남지 않는다.
            try (var guard = guard(resolve(key)); var lock = tryLock(guard)) {
                if (lock != null) {
                    settled.run();
                }
            }
        }

        @Override
        public InputStream open(String key) throws IOException {
            return Files.newInputStream(resolve(key));
        }

        @Override
        public void delete(String key) throws IOException {
            Path file = resolve(key);
            Files.deleteIfExists(file);
            Files.deleteIfExists(staged(file));
            Files.deleteIfExists(guardPath(file));
        }

        @Override
        public boolean exists(String key) {
            return Files.isRegularFile(resolve(key));
        }

        // 동일 키의 쓰기와 장애 후 정리에서 문서 전용 임시 파일을 찾는다.
        private static Path staged(Path target) {
            return target.resolveSibling(target.getFileName() + ".uploading");
        }

        // 쓰기 및 복구에서 같은 키의 OS 파일 잠금을 연다.
        private FileChannel guard(Path target) throws IOException {
            Files.createDirectories(root);
            return FileChannel.open(guardPath(target), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        }

        private static Path guardPath(Path target) {
            return target.resolveSibling(target.getFileName() + ".write-lock");
        }

        // 같은 JVM의 중복 잠금도 다른 프로세스의 쓰기와 같이 다음 정리로 미룬다.
        private static FileLock tryLock(FileChannel guard) throws IOException {
            try {
                return guard.tryLock();
            } catch (OverlappingFileLockException writing) {
                return null;
            }
        }

        // 생성한 객체 키만 이 저장소의 한 파일 이름으로 해석한다.
        private Path resolve(String key) {
            if (!key.matches("[a-z0-9-]{1,180}")) {
                throw new IllegalArgumentException("Invalid document object key");
            }
            return root.resolve(key);
        }
    }

    /** 기존 S3 클라이언트를 사용하되 문서용 비공개 버킷을 선택한다. */
    record PrivateBucket(S3Client client, String bucket) implements DocumentFiles {
        private static final Duration PUT_TIMEOUT = Duration.ofSeconds(20);
        private static final Duration METADATA_TIMEOUT = Duration.ofSeconds(5);

        @Override
        public void put(String key, Path source) throws IOException {
            long length = Files.size(source);
            try {
                client.putObject(request -> request.bucket(bucket).key(key)
                                .overrideConfiguration(options -> options.apiCallTimeout(PUT_TIMEOUT)),
                        RequestBody.fromFile(source));
            } catch (S3Exception rejected) {
                int status = rejected.statusCode();
                if (status >= 400 && status < 500 && status != 408 && status != 429) {
                    throw new IOException("Document object write rejected");
                }
                throw new UnconfirmedWrite();
            } catch (SdkException unavailable) {
                throw new UnconfirmedWrite();
            }

            // PUT 응답을 받은 뒤 일어난 HEAD 오류는 이미 끝난 객체를 삭제할 수 있는 확정 실패다.
            try {
                if (client.headObject(request -> request.bucket(bucket).key(key)
                        .overrideConfiguration(options -> options.apiCallTimeout(METADATA_TIMEOUT)))
                        .contentLength() != length) {
                    throw new IOException("Document object length check failed");
                }
            } catch (SdkException failure) {
                throw new IOException("Document object could not be stored");
            }
        }

        @Override
        public InputStream open(String key) throws IOException {
            try {
                return client.getObject(request -> request.bucket(bucket).key(key));
            } catch (SdkException failure) {
                throw new IOException("Document object could not be opened");
            }
        }

        @Override
        public void delete(String key) throws IOException {
            try {
                client.deleteObject(request -> request.bucket(bucket).key(key)
                        .overrideConfiguration(options -> options.apiCallTimeout(METADATA_TIMEOUT)));
            } catch (SdkException failure) {
                throw new IOException("Document object could not be removed");
            }
        }

        @Override
        public boolean exists(String key) throws IOException {
            try {
                client.headObject(request -> request.bucket(bucket).key(key)
                        .overrideConfiguration(options -> options.apiCallTimeout(METADATA_TIMEOUT)));
                return true;
            } catch (S3Exception absent) {
                if (absent.statusCode() == 404) {
                    return false;
                }
                throw new IOException("Document write settlement could not be checked");
            } catch (SdkException unavailable) {
                throw new IOException("Document write settlement could not be checked");
            }
        }
    }

    /** PUT이 완료되었는지 확인할 수 없어 객체 키의 추적을 계속해야 한다. */
    final class UnconfirmedWrite extends IOException {
        // 제공자 에러나 사용자 원본 경로가 공개 예외로 전파되지 않게 한다.
        UnconfirmedWrite() {
            super("Document object write has not been confirmed");
        }
    }
}
