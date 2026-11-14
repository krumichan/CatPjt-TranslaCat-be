package jp.co.translacat.domain.board;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
class BoardStorage {

    private final ObjectProvider<S3Client> clients;
    private final ObjectProvider<S3Presigner> presigners;

    @Value("${translacat.board.storage.bucket:}")
    private String bucket;

    @Value("${translacat.storage.s3.bucket:}")
    private String publicBucket;

    String start(UUID id, String filename) {
        // 원본 파일명은 다운로드 메타데이터에만 쓰고 객체 키는 서버에서 생성한다.
        String download = ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString();
        return request(() -> client().createMultipartUpload(CreateMultipartUploadRequest.builder()
                .bucket(bucket()).key(key(id)).contentDisposition(download)
                .contentType("application/octet-stream").cacheControl("private, no-store").build()).uploadId());
    }

    String signPart(UUID id, String uploadId, int number, long length) {
        // 브라우저가 Blob 크기로 생성하는 Content-Length를 서명에 포함한다.
        String destination = bucket();
        return request(() -> presigner().presignUploadPart(config -> config
                .signatureDuration(Duration.ofMinutes(30))
                .uploadPartRequest(req -> req.bucket(destination).key(key(id)).uploadId(uploadId)
                        .partNumber(number).contentLength(length))).url().toExternalForm());
    }

    List<Part> parts(UUID id, String uploadId) {
        return request(() -> client().listPartsPaginator(ListPartsRequest.builder()
                .bucket(bucket()).key(key(id)).uploadId(uploadId).build())
                .parts().stream().toList());
    }

    void complete(UUID id, String uploadId, List<Part> parts) {
        // 완료에는 R2가 응답한 ETag만 사용한다. 업로드 중인 파트가 바뀌면 R2가 완료를 거절한다.
        List<CompletedPart> completed = parts.stream()
                .map(part -> CompletedPart.builder().partNumber(part.partNumber()).eTag(part.eTag()).build())
                .toList();

        request(() -> client().completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(bucket()).key(key(id)).uploadId(uploadId)
                .multipartUpload(item -> item.parts(completed)).build()));
    }

    Long bytes(UUID id) {
        try {
            return client().headObject(req -> req.bucket(bucket()).key(key(id))).contentLength();
        } catch (S3Exception failure) {
            if (failure.statusCode() == 404) {
                return null;
            }
            throw BoardException.storage();
        } catch (SdkException failure) {
            throw BoardException.storage();
        }
    }

    String download(UUID id) {
        String destination = bucket();
        return request(() -> presigner().presignGetObject(config -> config
                .signatureDuration(Duration.ofSeconds(60))
                .getObjectRequest(req -> req.bucket(destination).key(key(id)))).url().toExternalForm());
    }

    void delete(UUID id, String uploadId) {
        // 업로드 상태를 먼저 종료해 서명된 파트 URL에서 객체를 다시 만들 수 없게 한다.
        try {
            client().abortMultipartUpload(req -> req.bucket(bucket()).key(key(id)).uploadId(uploadId));
        } catch (S3Exception failure) {
            if (failure.statusCode() != 404) {
                throw BoardException.storage();
            }
        } catch (SdkException failure) {
            throw BoardException.storage();
        }

        request(() -> client().deleteObject(req -> req.bucket(bucket()).key(key(id))));
    }

    private String bucket() {
        // 프로필처럼 공개 경로를 가진 기존 버킷으로의 저장을 차단한다.
        if (bucket.isBlank() || bucket.equals(publicBucket)) {
            throw BoardException.storage();
        }
        return bucket;
    }

    private S3Client client() {
        S3Client client = clients.getIfAvailable();
        if (client == null) {
            throw BoardException.storage();
        }
        return client;
    }

    private S3Presigner presigner() {
        S3Presigner presigner = presigners.getIfAvailable();
        if (presigner == null) {
            throw BoardException.storage();
        }
        return presigner;
    }

    private static String key(UUID id) {
        return "boards/" + id;
    }

    private <T> T request(Supplier<T> command) {
        try {
            return command.get();
        } catch (SdkException failure) {
            throw BoardException.storage();
        }
    }
}
