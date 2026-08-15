package jp.co.translacat.infrastructure.storage.s3;

import jp.co.translacat.domain.languagelearning.speaking.audio.model.SpeakingAudioObject;
import jp.co.translacat.domain.languagelearning.speaking.audio.port.SpeakingAudioStoragePort;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.storage.config.StorageProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@ConditionalOnProperty(
        prefix = "translacat.storage",
        name = "type",
        havingValue = "s3"
)
public class S3CompatibleSpeakingAudioStorageAdapter
        implements SpeakingAudioStoragePort {

    private final S3Client s3Client;
    private final String bucket;

    public S3CompatibleSpeakingAudioStorageAdapter(
            S3Client s3Client,
            StorageProperties properties
    ) {
        this.s3Client = s3Client;
        this.bucket = properties.getS3().getBucket();
    }

    @Override
    public void store(
            String objectKey,
            byte[] bytes,
            String contentType
    ) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .cacheControl("private, no-store")
                .build();

        try {
            s3Client.putObject(
                    request,
                    RequestBody.fromBytes(bytes)
            );
        } catch (SdkException e) {
            throw new BusinessException(
                    "Speaking Audio 업로드에 실패했습니다.",
                    e
            );
        }
    }

    @Override
    public SpeakingAudioObject load(
            String objectKey,
            String contentType
    ) {
        try {
            byte[] bytes = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .build()
            ).asByteArray();

            return new SpeakingAudioObject(
                    objectKey,
                    bytes,
                    contentType
            );
        } catch (SdkException e) {
            throw new BusinessException(
                    "Speaking Audio 조회에 실패했습니다.",
                    e
            );
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .build()
            );
        } catch (SdkException e) {
            throw new BusinessException(
                    "Speaking Audio 삭제에 실패했습니다.",
                    e
            );
        }
    }
}
