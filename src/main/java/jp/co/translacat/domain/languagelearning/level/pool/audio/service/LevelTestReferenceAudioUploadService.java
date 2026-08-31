package jp.co.translacat.domain.languagelearning.level.pool.audio.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestReferenceAudioDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestReferenceAudioUploadDto;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;
import jp.co.translacat.domain.languagelearning.level.pool.audio.model.LevelTestReferenceAudioUpload;
import jp.co.translacat.domain.languagelearning.level.pool.audio.port.LevelTestReferenceAudioUploadPort;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;

@Slf4j
@Service
public class LevelTestReferenceAudioUploadService {

    private static final String CONTENT_TYPE = "audio/wav";
    private static final String VOICE = "Kore";
    private static final String PLAYBACK_SPEED = "NORMAL";

    private final LevelTestReferenceAudioUploadPort uploadPort;
    private final Duration uploadTtl;

    public LevelTestReferenceAudioUploadService(
            ObjectProvider<LevelTestReferenceAudioUploadPort> uploadPortProvider,
            @Value("${language-learning.level-test.question-pool.reference-audio-upload-ttl-seconds:300}")
            long uploadTtlSeconds
    ) {
        this.uploadPort = uploadPortProvider.getIfAvailable();
        this.uploadTtl = Duration.ofSeconds(
                Math.max(60L, Math.min(3600L, uploadTtlSeconds))
        );
    }

    public boolean available() {
        return uploadPort != null;
    }

    public boolean requiresReferenceAudio(
            LevelTestDomain domain,
            LevelTestItemType itemType
    ) {
        return domain == LevelTestDomain.LISTENING
                || itemType == LevelTestItemType.SPEAKING_REPEAT;
    }

    public LevelTestReferenceAudioUploadDto prepare(
            String learningLanguage,
            String idempotencyKey,
            LevelTestDomain domain,
            LevelTestItemType itemType
    ) {
        if (!requiresReferenceAudio(domain, itemType) || uploadPort == null) {
            return null;
        }
        String objectKey = objectKey(learningLanguage, idempotencyKey);
        LevelTestReferenceAudioUpload upload = uploadPort.prepare(
                objectKey,
                CONTENT_TYPE,
                uploadTtl
        );
        return new LevelTestReferenceAudioUploadDto(
                upload.uploadUrl(),
                upload.objectKey(),
                upload.contentType(),
                VOICE,
                PLAYBACK_SPEED
        );
    }

    public void verify(LevelTestReferenceAudioDto audio) {
        if (audio == null) {
            return;
        }
        if (uploadPort == null
                || audio.objectKey() == null
                || audio.objectKey().isBlank()
                || !uploadPort.exists(audio.objectKey())) {
            throw new BusinessException(
                    "Level Test Reference Audio 업로드 확인에 실패했습니다.",
                    LanguageLearningErrorCode.AI_TTS_FAILED
            );
        }
    }

    public void cleanupQuietly(LevelTestReferenceAudioDto audio) {
        if (audio == null || uploadPort == null || audio.objectKey() == null) {
            return;
        }
        try {
            uploadPort.delete(audio.objectKey());
        } catch (RuntimeException exception) {
            log.warn(
                    "Level Test orphan reference audio cleanup failed. objectKey={}",
                    audio.objectKey(),
                    exception
            );
        }
    }

    private String objectKey(
            String learningLanguage,
            String idempotencyKey
    ) {
        String language = learningLanguage == null
                || learningLanguage.isBlank()
                ? "unknown"
                : learningLanguage.replaceAll("[^A-Za-z0-9_-]", "_");
        return "language-learning/level-test/reference/"
                + language
                + "/"
                + sha256(idempotencyKey)
                + ".wav";
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            Objects.requireNonNullElse(value, "")
                                    .getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
