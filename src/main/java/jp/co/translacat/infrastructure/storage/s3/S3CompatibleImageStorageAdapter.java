package jp.co.translacat.infrastructure.storage.s3;

import jp.co.translacat.domain.user.profile.storage.model.ImageStorageUpload;
import jp.co.translacat.domain.user.profile.storage.port.ImageStoragePort;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.storage.config.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@ConditionalOnProperty(
        prefix = "translacat.storage",
        name = "type",
        havingValue = "s3"
)
public class S3CompatibleImageStorageAdapter
        implements ImageStoragePort {

    private final S3Client s3Client;
    private final String bucket;
    private final String publicBaseUrl;

    public S3CompatibleImageStorageAdapter(
            S3Client s3Client,
            StorageProperties properties
    ) {
        this.s3Client = s3Client;
        this.bucket = properties.getS3().getBucket();
        this.publicBaseUrl = stripTrailingSlash(
                properties.getS3().getPublicBaseUrl()
        );
    }

    @Override
    public void store(ImageStorageUpload upload) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(upload.objectKey())
                .contentType(upload.contentType())
                .cacheControl("public, max-age=31536000, immutable")
                .build();

        try {
            s3Client.putObject(
                    request,
                    RequestBody.fromBytes(upload.bytes())
            );
        } catch (SdkException e) {
            throw new BusinessException(
                    "이미지 저장소 업로드에 실패했습니다.",
                    e
            );
        }
    }

    @Override
    public void delete(String objectKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        try {
            s3Client.deleteObject(request);
        } catch (SdkException e) {
            throw new BusinessException(
                    "이미지 저장소 삭제에 실패했습니다.",
                    e
            );
        }
    }

    @Override
    public String resolvePublicUrl(String objectKey) {
        return publicBaseUrl + "/" + objectKey;
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "STORAGE_S3_PUBLIC_BASE_URL 설정이 필요합니다."
            );
        }

        return value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }
}
