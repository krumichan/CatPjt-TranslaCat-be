package jp.co.translacat.infrastructure.storage.local;

import jp.co.translacat.domain.user.profile.storage.model.ImageStorageUpload;
import jp.co.translacat.domain.user.profile.storage.port.ImageStoragePort;
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
public class LocalImageStorageAdapter implements ImageStoragePort {

    private final Path rootPath;
    private final String publicBaseUrl;

    public LocalImageStorageAdapter(StorageProperties properties) {
        this.rootPath = Path.of(
                properties.getLocal().getRootPath()
        ).toAbsolutePath().normalize();

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
    public String resolvePublicUrl(String objectKey) {
        return publicBaseUrl + "/" + objectKey;
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
