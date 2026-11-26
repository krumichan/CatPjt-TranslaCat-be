package jp.co.translacat.domain.documenttranslation;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Path;
import java.util.Optional;

/** API 기능 스위치를 내려도 기존 문서의 비공개 저장소에 정리 작업이 접근할 수 있게 한다. */
@Component
final class DocumentStorageAccess {
    private final ObjectProvider<S3Client> clients;
    private final String bucket;
    private final String imageBucket;
    private final String localDirectory;

    // 다른 서비스 버킷을 사용하지 않고 문서 전용 설정만 읽는다.
    DocumentStorageAccess(ObjectProvider<S3Client> clients,
            @Value("${translacat.document-translation.private-bucket:}") String bucket,
            @Value("${translacat.storage.s3.bucket:}") String imageBucket,
            @Value("${translacat.document-translation.local-directory:}") String localDirectory) {
        this.clients = clients;
        this.bucket = bucket;
        this.imageBucket = imageBucket;
        this.localDirectory = localDirectory;
    }

    // 문서 기능이 꺼졌고 저장소를 설정한 적이 없다면 삭제 모듈은 파일 작업 전에 상태를 구분한다.
    Optional<DocumentFiles> configured() {
        if (!bucket.isBlank()) {
            if (!localDirectory.isBlank() || bucket.equals(imageBucket)) {
                throw new IllegalStateException("Document translation requires a separate private object store");
            }
            return Optional.of(new DocumentFiles.PrivateBucket(clients.getObject(), bucket));
        }
        if (!localDirectory.isBlank()) {
            return Optional.of(new DocumentFiles.Local(Path.of(localDirectory).toAbsolutePath()));
        }
        return Optional.empty();
    }
}
