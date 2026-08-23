package jp.co.translacat.infrastructure.storage.local;

import jp.co.translacat.domain.languagelearning.listening.audio.model.ListeningAudioObject;
import jp.co.translacat.domain.languagelearning.listening.audio.port.ListeningAudioStoragePort;
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
public class LocalListeningAudioStorageAdapter
        implements ListeningAudioStoragePort {

    private final Path rootPath;

    public LocalListeningAudioStorageAdapter(StorageProperties properties) {
        rootPath = Path.of(properties.getLocal().getRootPath())
                .toAbsolutePath().normalize();
    }

    @Override
    public void store(String objectKey, byte[] bytes, String contentType) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(
                    target,
                    bytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            throw new BusinessException(
                    "Listening Audio 저장에 실패했습니다.",
                    exception
            );
        }
    }

    @Override
    public ListeningAudioObject load(String objectKey, String contentType) {
        try {
            return new ListeningAudioObject(
                    objectKey,
                    Files.readAllBytes(resolve(objectKey)),
                    contentType
            );
        } catch (IOException exception) {
            throw new BusinessException(
                    "Listening Audio 조회에 실패했습니다.",
                    exception
            );
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException exception) {
            throw new BusinessException(
                    "Listening Audio 삭제에 실패했습니다.",
                    exception
            );
        }
    }

    private Path resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(
                    "Listening Audio Object Key가 필요합니다."
            );
        }
        Path value = rootPath.resolve(objectKey).normalize();
        if (!value.startsWith(rootPath)) {
            throw new BusinessException(
                    "잘못된 Listening Audio 경로입니다."
            );
        }
        return value;
    }
}
