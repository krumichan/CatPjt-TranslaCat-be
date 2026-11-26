package jp.co.translacat.domain.documenttranslation;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
import java.util.Map;
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
        private static final Duration PART_TIMEOUT = Duration.ofSeconds(20);
        private static final Duration METADATA_TIMEOUT = Duration.ofSeconds(5);
        private static final String WRITE_ID_METADATA = "document-write-id";
        private static final int DOCUMENT_PART = 1;

        // 비공개 버킷의 쓰기는 원장을 통해 기록한 업로드 ID가 있어야 한다.
        @Override
        public void put(String key, Path source) throws IOException {
            throw new IOException("Document bucket writes require a tracked multipart upload");
        }

        // 시도별 객체 키의 업로드를 생성한다. 반환한 ID를 영속화하기 전에는 본문을 쓰지 않는다.
        String createMultipart(String key) throws IOException {
            try {
                return requireUploadId(client.createMultipartUpload(request -> request.bucket(bucket).key(key)
                                .metadata(Map.of(WRITE_ID_METADATA, key))
                                .overrideConfiguration(options -> options.apiCallTimeout(METADATA_TIMEOUT)))
                        .uploadId());
            } catch (SdkException failure) {
                throw new UnconfirmedWrite();
            }
        }

        // 기록된 업로드의 마지막 단일 파트를 저장하고 소유권과 길이를 확인한다.
        void writeMultipart(String key, String uploadId, Path source) throws IOException {
            requireUploadId(uploadId);
            long length = Files.size(source);
            try {
                // 마지막 파트는 5 MiB 미만도 허용하며, 완료 시점에 같은 키의 기존 객체를 보호한다.
                String partTag = client.uploadPart(request -> request.bucket(bucket).key(key)
                                .uploadId(uploadId).partNumber(DOCUMENT_PART).contentLength(length)
                                .overrideConfiguration(options -> options.apiCallTimeout(PART_TIMEOUT)),
                        RequestBody.fromFile(source)).eTag();
                if (partTag == null || partTag.isBlank()) {
                    throw new UnconfirmedWrite();
                }
                CompletedPart part = CompletedPart.builder().partNumber(DOCUMENT_PART).eTag(partTag).build();
                client.completeMultipartUpload(request -> request.bucket(bucket).key(key)
                        .uploadId(uploadId).ifNoneMatch("*")
                        .multipartUpload(CompletedMultipartUpload.builder().parts(part).build())
                        .overrideConfiguration(options -> options.apiCallTimeout(PART_TIMEOUT)));

                // 완료 요청 이후의 조회 실패도 원장에 남겨 같은 업로드를 재정리한다.
                HeadObjectResponse stored = head(key);
                if (!Long.valueOf(length).equals(stored.contentLength()) || !owns(key, stored)) {
                    throw new UnconfirmedWrite();
                }
            } catch (SdkException | UncheckedIOException failure) {
                throw new UnconfirmedWrite();
            }
        }

        // 기존 업로드를 중단하고 같은 ID가 더 이상 완료될 수 없음을 확인한다.
        void abortMultipart(String key, String uploadId) throws IOException {
            requireUploadId(uploadId);
            try {
                client.abortMultipartUpload(request -> request.bucket(bucket).key(key).uploadId(uploadId)
                        .overrideConfiguration(options -> options.apiCallTimeout(METADATA_TIMEOUT)));
            } catch (S3Exception failure) {
                if (!missingUpload(failure)) {
                    throw new UnconfirmedWrite();
                }
            } catch (SdkException unavailable) {
                throw new UnconfirmedWrite();
            }

            // 중단 응답이나 빈 파트 목록으로는 이전 완료 요청의 종료를 입증하지 않는다.
            try {
                client.listParts(request -> request.bucket(bucket).key(key).uploadId(uploadId).maxParts(1)
                        .overrideConfiguration(options -> options.apiCallTimeout(METADATA_TIMEOUT)));
            } catch (S3Exception failure) {
                if (missingUpload(failure)) {
                    return;
                }
                throw new UnconfirmedWrite();
            } catch (SdkException unavailable) {
                throw new UnconfirmedWrite();
            }
            throw new UnconfirmedWrite();
        }

        // 완료가 차단된 시도 전용 키에 그 시도가 남긴 객체만 정리한다.
        void reconcileMultipart(String key, String uploadId) throws IOException {
            abortMultipart(key, uploadId);
            HeadObjectResponse stored;
            try {
                stored = head(key);
            } catch (S3Exception failure) {
                if (failure.statusCode() == 404 && !hasErrorCode(failure, "NoSuchBucket")) {
                    return;
                }
                throw new UnconfirmedWrite();
            } catch (SdkException unavailable) {
                throw new UnconfirmedWrite();
            }

            // 키는 시도마다 새로 만들며 정리 이후 같은 키에 업로드를 다시 시작할 수 없다.
            if (!owns(key, stored)) {
                throw new UnconfirmedWrite();
            }
            delete(key);
        }

        // 공급자가 반환한 업로드 ID를 그대로 사용한다. 누락된 ID의 종료는 확정할 수 없다.
        private static String requireUploadId(String uploadId) throws IOException {
            if (uploadId == null || uploadId.isBlank()) {
                throw new UnconfirmedWrite();
            }
            return uploadId;
        }

        // 404만으로는 특정 업로드의 종료가 확인되지 않는다.
        private static boolean missingUpload(S3Exception failure) {
            return failure.statusCode() == 404 && hasErrorCode(failure, "NoSuchUpload");
        }

        // SDK가 상세 코드 없이 반환한 오류는 이 코드에 해당하지 않는다.
        private static boolean hasErrorCode(S3Exception failure, String code) {
            return failure.awsErrorDetails() != null && code.equals(failure.awsErrorDetails().errorCode());
        }

        // 내용이 같은 다른 객체와 현재 시도의 객체를 작성 ID로 구분한다.
        private static boolean owns(String key, HeadObjectResponse stored) {
            return key.equals(stored.metadata().get(WRITE_ID_METADATA));
        }

        // 조정 및 저장 확인은 캐시를 통하지 않는 기존 버킷 클라이언트의 HEAD를 사용한다.
        private HeadObjectResponse head(String key) {
            return client.headObject(request -> request.bucket(bucket).key(key)
                    .overrideConfiguration(options -> options.apiCallTimeout(METADATA_TIMEOUT)));
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
                head(key);
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

    /** 공급자 쓰기 또는 정리가 확정되지 않아 객체 키의 추적을 계속해야 한다. */
    final class UnconfirmedWrite extends IOException {
        // 제공자 에러나 사용자 원본 경로가 공개 예외로 전파되지 않게 한다.
        UnconfirmedWrite() {
            super("Document object write has not been confirmed");
        }
    }
}
