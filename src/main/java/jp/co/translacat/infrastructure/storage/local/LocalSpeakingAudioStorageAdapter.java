package jp.co.translacat.infrastructure.storage.local;

import jp.co.translacat.domain.languagelearning.speaking.audio.model.SpeakingAudioObject;
import jp.co.translacat.domain.languagelearning.speaking.audio.port.SpeakingAudioStoragePort;
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
public class LocalSpeakingAudioStorageAdapter
        implements SpeakingAudioStoragePort {

    private final Path rootPath;

    public LocalSpeakingAudioStorageAdapter(StorageProperties properties) {
        this.rootPath = Path.of(
                properties.getLocal().getRootPath()
        ).toAbsolutePath().normalize();
    }

    @Override
    public void store(
            String objectKey,
            byte[] bytes,
            String contentType
    ) {
        Path targetPath = resolveSafePath(objectKey);

        try {
            Files.createDirectories(targetPath.getParent());
            Files.write(
                    targetPath,
                    bytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            throw new BusinessException(
                    "Speaking Audio 저장에 실패했습니다.",
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
            return new SpeakingAudioObject(
                    objectKey,
                    Files.readAllBytes(resolveSafePath(objectKey)),
                    contentType
            );
        } catch (IOException e) {
            throw new BusinessException(
                    "Speaking Audio 조회에 실패했습니다.",
                    e
            );
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolveSafePath(objectKey));
        } catch (IOException e) {
            throw new BusinessException(
                    "Speaking Audio 삭제에 실패했습니다.",
                    e
            );
        }
    }

    private Path resolveSafePath(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(
                    "Speaking Audio Object Key가 필요합니다."
            );
        }

        Path resolved = rootPath.resolve(objectKey).normalize();

        if (!resolved.startsWith(rootPath)) {
            throw new BusinessException(
                    "잘못된 Speaking Audio 경로입니다."
            );
        }

        return resolved;
    }
}
