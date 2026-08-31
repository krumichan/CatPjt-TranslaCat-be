package jp.co.translacat.domain.languagelearning.level.service;

import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestResponse;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestResponseRepository;
import jp.co.translacat.domain.languagelearning.speaking.audio.port.SpeakingAudioStoragePort;
import jp.co.translacat.domain.languagelearning.speaking.audio.service.SpeakingAudioKeyFactory;
import jp.co.translacat.domain.languagelearning.speaking.audio.validator.SpeakingAudioSignatureValidator;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LevelTestAudioService {

    private static final long MAX_AUDIO_BYTES = 10L * 1024L * 1024L;
    private static final int RETENTION_DAYS = 7;

    private final SpeakingAudioStoragePort storagePort;
    private final SpeakingAudioKeyFactory keyFactory;
    private final SpeakingAudioSignatureValidator signatureValidator;
    private final LevelTestAnswerCommandService answerCommandService;
    private final LevelTestResponseRepository responseRepository;

    public StoredAudio prepareAndStore(
            Long userId,
            Long sessionId,
            Long itemId,
            MultipartFile audio,
            Integer durationMs,
            String idempotencyKey
    ) {
        validateAudio(audio, durationMs);

        String previousObjectKey = responseRepository.findByItemId(itemId)
                .map(LevelTestResponse::getAudioObjectKey)
                .orElse(null);
        byte[] bytes = readBytes(audio);
        String contentType = audio.getContentType();
        signatureValidator.validate(bytes, contentType);

        String objectKey = keyFactory.userTurn(
                userId,
                sessionId,
                itemId,
                extension(audio.getOriginalFilename())
        );
        LocalDateTime retentionUntil = LocalDateTime.now()
                .plusDays(RETENTION_DAYS);
        storagePort.store(objectKey, bytes, contentType);

        try {
            LevelTestAnswerCommandService.PreparedResponse prepared =
                    answerCommandService.prepareAudioMetadata(
                            userId,
                            sessionId,
                            itemId,
                            objectKey,
                            contentType,
                            durationMs,
                            idempotencyKey,
                            retentionUntil
                    );
            if (prepared.idempotentReplay()) {
                if (!objectKey.equals(
                        prepared.response().getAudioObjectKey()
                )) {
                    storagePort.delete(objectKey);
                }
                byte[] replayBytes = load(prepared.response());
                return new StoredAudio(
                        prepared.item(),
                        prepared.response(),
                        true,
                        prepared.response().getAudioRetentionUntil(),
                        replayBytes,
                        audio.getOriginalFilename(),
                        prepared.response().getAudioContentType()
                );
            }
            if (previousObjectKey != null
                    && !previousObjectKey.equals(prepared.response().getAudioObjectKey())) {
                deleteQuietly(previousObjectKey);
            }
            return new StoredAudio(
                    prepared.item(),
                    prepared.response(),
                    false,
                    prepared.response().getAudioRetentionUntil(),
                    bytes,
                    audio.getOriginalFilename(),
                    contentType
            );
        } catch (RuntimeException exception) {
            deleteQuietly(objectKey);
            throw exception;
        }
    }

    public byte[] load(LevelTestResponse response) {
        if (response.getAudioObjectKey() == null) {
            throw new BusinessException(
                    "재평가할 Level Test Audio가 없습니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_AUDIO_INVALID
            );
        }
        return storagePort.load(
                response.getAudioObjectKey(),
                response.getAudioContentType()
        ).bytes();
    }

    private void validateAudio(MultipartFile audio, Integer durationMs) {
        if (audio == null || audio.isEmpty()) {
            throw invalid("Level Test Audio 파일이 필요합니다.");
        }
        if (audio.getSize() <= 0 || audio.getSize() > MAX_AUDIO_BYTES) {
            throw invalid(
                    "Level Test Audio 파일 크기가 허용 범위를 벗어났습니다."
            );
        }
        if (durationMs == null || durationMs <= 0) {
            throw invalid("Level Test Audio 길이가 필요합니다.");
        }
        String contentType = audio.getContentType();
        if (contentType == null
                || !contentType.toLowerCase().startsWith("audio/")) {
            throw invalid(
                    "지원하지 않는 Level Test Audio Content-Type입니다."
            );
        }
    }

    private byte[] readBytes(MultipartFile audio) {
        try {
            byte[] bytes = audio.getBytes();
            if (bytes.length == 0 || bytes.length > MAX_AUDIO_BYTES) {
                throw invalid(
                        "Level Test Audio 파일 크기가 허용 범위를 벗어났습니다."
                );
            }
            return bytes;
        } catch (IOException exception) {
            throw new BusinessException(
                    "Level Test Audio 파일을 읽을 수 없습니다.",
                    exception
            );
        }
    }

    private String extension(String name) {
        if (name == null || !name.contains(".")) {
            return "bin";
        }
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private void deleteQuietly(String objectKey) {
        try {
            storagePort.delete(objectKey);
        } catch (RuntimeException ignored) {
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.LEVEL_TEST_AUDIO_INVALID
        );
    }

    public record StoredAudio(
            LevelTestItem item,
            LevelTestResponse response,
            boolean idempotentReplay,
            LocalDateTime retentionUntil,
            byte[] bytes,
            String fileName,
            String contentType
    ) {
    }
}
