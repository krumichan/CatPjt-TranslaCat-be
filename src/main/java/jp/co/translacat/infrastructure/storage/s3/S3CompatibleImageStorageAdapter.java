package jp.co.translacat.infrastructure.storage.s3;

import jp.co.translacat.domain.user.profile.storage.model.ImageStorageUpload;
import jp.co.translacat.domain.user.profile.storage.port.ImageStoragePort;
import jp.co.translacat.novel.application.NovelAudioObjectStore;
import jp.co.translacat.novel.domain.NovelProblem;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.storage.config.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

@Component
@ConditionalOnProperty(
        prefix = "translacat.storage",
        name = "type",
        havingValue = "s3"
)
public class S3CompatibleImageStorageAdapter
        implements ImageStoragePort, NovelAudioObjectStore {

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

    @Override
    public void put(String objectKey, Path file, String contentType, long bytes, String sha256) {
        requireAudioKey(objectKey);
        try {
            // 기존 이미지 R2 bucket을 공유한다. 재생 응답은 BE가 전달하며 bucket 자체는 공개 접근 가능하다.
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucket).key(objectKey).contentType(contentType)
                    .contentLength(bytes).cacheControl("private, max-age=31536000, immutable")
                    .metadata(Map.of("sha256", sha256)).build(), RequestBody.fromFile(file));
        } catch (SdkException failure) {
            throw unavailable();
        }
    }

    @Override
    public ObjectInfo head(String objectKey) {
        requireAudioKey(objectKey);
        try {
            var response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket).key(objectKey).build());
            return new ObjectInfo(response.contentLength(), response.metadata().get("sha256"),
                    response.contentType());
        } catch (S3Exception failure) {
            if (failure.statusCode() == 404) throw new NovelAudioObjectStore.Missing();
            throw unavailable();
        } catch (SdkException failure) {
            throw unavailable();
        }
    }

    @Override
    public ObjectData read(String objectKey, int maxBytes) {
        requireAudioKey(objectKey);
        try (var stream = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket).key(objectKey).build())) {
            byte[] data = stream.readNBytes(maxBytes + 1);
            if (data.length > maxBytes) throw new NovelProblem("AUDIO_OBJECT_TOO_LARGE", 502);
            return new ObjectData(data, stream.response().contentType());
        } catch (S3Exception failure) {
            if (failure.statusCode() == 404) throw new NovelAudioObjectStore.Missing();
            throw unavailable();
        } catch (IOException | SdkException failure) {
            throw unavailable();
        }
    }

    private static void requireAudioKey(String key) {
        if (key == null || !key.matches("novel/audio/v1/[0-9a-f]{64}/[0-9]+\\.wav")) {
            throw new NovelProblem("AUDIO_OBJECT_KEY_INVALID", 400);
        }
    }

    private static NovelProblem unavailable() {
        return new NovelProblem("AUDIO_STORAGE_UNAVAILABLE", 503, true, 1000);
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
