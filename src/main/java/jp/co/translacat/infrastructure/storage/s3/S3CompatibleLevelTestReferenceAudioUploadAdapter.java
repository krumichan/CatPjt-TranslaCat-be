package jp.co.translacat.infrastructure.storage.s3;

import jp.co.translacat.domain.languagelearning.level.pool.audio.model.LevelTestReferenceAudioUpload;
import jp.co.translacat.domain.languagelearning.level.pool.audio.port.LevelTestReferenceAudioUploadPort;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.storage.config.StorageProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Component
@ConditionalOnProperty(
        prefix = "translacat.storage",
        name = "type",
        havingValue = "s3"
)
public class S3CompatibleLevelTestReferenceAudioUploadAdapter
        implements LevelTestReferenceAudioUploadPort {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3CompatibleLevelTestReferenceAudioUploadAdapter(
            S3Client s3Client,
            S3Presigner presigner,
            StorageProperties properties
    ) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.bucket = properties.getS3().getBucket();
    }

    @Override
    public LevelTestReferenceAudioUpload prepare(
            String objectKey,
            String contentType,
            Duration ttl
    ) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build();
            String url = presigner.presignPutObject(
                    PutObjectPresignRequest.builder()
                            .signatureDuration(ttl)
                            .putObjectRequest(request)
                            .build()
            ).url().toString();
            return new LevelTestReferenceAudioUpload(
                    url,
                    objectKey,
                    contentType
            );
        } catch (SdkException exception) {
            throw new BusinessException(
                    "Level Test Reference Audio 업로드 URL 생성에 실패했습니다.",
                    exception
            );
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw new BusinessException(
                    "Level Test Reference Audio 업로드 확인에 실패했습니다.",
                    exception
            );
        } catch (SdkException exception) {
            throw new BusinessException(
                    "Level Test Reference Audio 업로드 확인에 실패했습니다.",
                    exception
            );
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
        } catch (SdkException exception) {
            throw new BusinessException(
                    "Level Test Reference Audio 삭제에 실패했습니다.",
                    exception
            );
        }
    }
}
