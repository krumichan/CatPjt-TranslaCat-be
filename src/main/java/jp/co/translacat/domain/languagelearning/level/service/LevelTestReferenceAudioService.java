package jp.co.translacat.domain.languagelearning.level.service;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.ai.port.ListeningAiClient;
import jp.co.translacat.domain.languagelearning.listening.audio.model.ListeningAudioObject;
import jp.co.translacat.domain.languagelearning.listening.audio.port.ListeningAudioStoragePort;
import jp.co.translacat.domain.languagelearning.listening.audio.service.ListeningAudioKeyFactory;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LevelTestReferenceAudioService {

    private static final String DEFAULT_VOICE = "Kore";

    private final ListeningAiClient aiClient;
    private final ListeningAudioStoragePort storagePort;
    private final ListeningAudioKeyFactory keyFactory;
    private final ListeningPolicySettingQueryService policySettingQueryService;
    private final LevelTestQuestionPersistenceService persistenceService;
    private final LanguageLearningJsonCodec jsonCodec;

    public void ensureReferenceAudio(LevelTestItem item) {
        if (!requiresReferenceAudio(item)
                || item.getReferenceAudioObjectKey() != null) {
            return;
        }

        String text = referenceText(item);
        if (text == null || text.isBlank()) {
            throw new BusinessException(
                    "Level Test Reference Audio 원문이 없습니다.",
                    LanguageLearningErrorCode.AI_SCHEMA_INVALID
            );
        }

        var policy = policySettingQueryService.get();
        String hash = sha256(text);
        var response = aiClient.synthesize(
                new AiListeningContract.TtsRequest(
                        "level-test-tts-" + item.getId(),
                        "level-test-tts-" + item.getId(),
                        item.getId(),
                        text,
                        hash,
                        item.getGenerationVersion(),
                        item.getSession().getLearningLanguage(),
                        new AiListeningContract.Voice(
                                item.getSession().getLearningLanguage(),
                                DEFAULT_VOICE,
                                "v1",
                                "STANDARD"
                        ),
                        "NORMAL",
                        policy.getProfilePolicyVersion(),
                        policy.getModelConfigVersion(),
                        policy.getAutomaticRetryLimit(),
                        0
                )
        );

        if (response == null
                || response.audio() == null
                || response.audio().audioReference() == null) {
            throw new BusinessException(
                    "Level Test TTS 생성에 실패했습니다.",
                    LanguageLearningErrorCode.AI_TTS_FAILED
            );
        }

        byte[] bytes = aiClient.getAudio(response.audio().audioReference());
        String objectKey = keyFactory.reference(
                item.getSession().getUser().getId(),
                item.getSession().getId(),
                item.getId(),
                "wav"
        );
        storagePort.store(objectKey, bytes, "audio/wav");
        persistenceService.attachReferenceAudio(
                item.getId(),
                objectKey,
                "audio/wav"
        );
    }

    public ListeningAudioObject load(LevelTestItem item) {
        if (item.getReferenceAudioObjectKey() == null) {
            throw new BusinessException(
                    "Level Test Reference Audio가 준비되지 않았습니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }
        return storagePort.load(
                item.getReferenceAudioObjectKey(),
                item.getReferenceAudioContentType()
        );
    }

    private boolean requiresReferenceAudio(LevelTestItem item) {
        return item.getDomain() == LevelTestDomain.LISTENING
                || item.getItemType() == LevelTestItemType.SPEAKING_REPEAT;
    }

    private String referenceText(LevelTestItem item) {
        Map<String, Object> payload = jsonCodec.read(
                item.getReferencePayloadJson(),
                new TypeReference<Map<String, Object>>() {
                }
        );
        Object value = item.getItemType() == LevelTestItemType.SPEAKING_REPEAT
                ? payload.get("referenceText")
                : payload.get("sourceText");
        return value == null ? null : String.valueOf(value);
    }

    private String sha256(String text) {
        try {
            String normalized = Normalizer.normalize(
                    text,
                    Normalizer.Form.NFKC
            ).trim();
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            normalized.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
