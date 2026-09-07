package jp.co.translacat.domain.languagelearning.level.service;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionStatus;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestEvaluation;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestEvaluationRepository;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestItemRepository;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestResponseRepository;
import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.ai.port.ListeningAiClient;
import jp.co.translacat.domain.languagelearning.listening.audio.model.ListeningAudioObject;
import jp.co.translacat.domain.languagelearning.listening.audio.port.ListeningAudioStoragePort;
import jp.co.translacat.domain.languagelearning.listening.audio.service.ListeningAudioKeyFactory;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.languagelearning.speaking.audio.port.SpeakingAudioStoragePort;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LevelTestReviewAudioService {

    private static final String DEFAULT_VOICE = "Kore";

    private final LevelTestItemRepository itemRepository;
    private final LevelTestResponseRepository responseRepository;
    private final LevelTestEvaluationRepository evaluationRepository;
    private final LevelTestReferenceAudioService referenceAudioService;
    private final ListeningAiClient aiClient;
    private final ListeningAudioStoragePort storagePort;
    private final SpeakingAudioStoragePort speakingAudioStoragePort;
    private final ListeningAudioKeyFactory keyFactory;
    private final ListeningPolicySettingQueryService policySettingQueryService;
    private final LevelTestQuestionPersistenceService persistenceService;
    private final LanguageLearningJsonCodec jsonCodec;

    public ListeningAudioObject loadReferenceAudio(Long userId, Long itemId) {
        LevelTestItem item = completedOwnedItem(userId, itemId);
        return referenceAudioService.load(item);
    }

    public ListeningAudioObject loadAnswerAudio(Long userId, Long itemId) {
        LevelTestItem item = completedOwnedItem(userId, itemId);
        var response = responseRepository.findByItemId(item.getId())
                .orElseThrow(() -> new BusinessException(
                        "Level Test 음성 답변을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.LEVEL_TEST_NOT_FOUND
                ));
        if (response.getAudioObjectKey() == null || response.getAudioObjectKey().isBlank()) {
            throw new BusinessException(
                    "Level Test 음성 답변을 찾을 수 없습니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_NOT_FOUND
            );
        }
        var audio = speakingAudioStoragePort.load(
                response.getAudioObjectKey(),
                response.getAudioContentType()
        );
        return new ListeningAudioObject(
                audio.objectKey(),
                audio.bytes(),
                audio.contentType()
        );
    }

    public ListeningAudioObject loadModelAnswerAudio(Long userId, Long itemId) {
        LevelTestItem item = completedOwnedItem(userId, itemId);
        if (item.getItemType() == LevelTestItemType.SPEAKING_REPEAT) {
            return referenceAudioService.load(item);
        }
        if (item.getItemType() != LevelTestItemType.SPEAKING_GUIDED_RESPONSE
                && item.getItemType() != LevelTestItemType.SPEAKING_SHORT_RESPONSE) {
            throw new BusinessException(
                    "모범 답안 음성을 제공하지 않는 Level Test 문항입니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }
        if (item.getModelAnswerAudioObjectKey() != null) {
            return storagePort.load(
                    item.getModelAnswerAudioObjectKey(),
                    item.getModelAnswerAudioContentType()
            );
        }

        String text = firstRecommendedAnswer(item);
        if (text == null || text.isBlank()) {
            throw new BusinessException(
                    "Level Test 모범 답안이 준비되지 않았습니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }

        var policy = policySettingQueryService.get();
        String hash = sha256(text);
        var response = aiClient.synthesize(
                new AiListeningContract.TtsRequest(
                        "level-test-model-answer-tts-" + item.getId(),
                        "level-test-model-answer-tts-" + item.getId(),
                        item.getId(),
                        text,
                        hash,
                        item.getGenerationVersion(),
                        item.getSession().getLearningLanguage(),
                        new AiListeningContract.Voice(
                                item.getSession().getLearningLanguage(),
                                DEFAULT_VOICE,
                                "current",
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
                    "Level Test 모범 답안 TTS 생성에 실패했습니다.",
                    LanguageLearningErrorCode.AI_TTS_FAILED
            );
        }

        byte[] bytes = aiClient.getAudio(response.audio().audioReference());
        String objectKey = keyFactory.levelTestModelAnswer(
                item.getSession().getUser().getId(),
                item.getSession().getId(),
                item.getId(),
                "wav"
        );
        storagePort.store(objectKey, bytes, "audio/wav");
        persistenceService.attachModelAnswerAudio(item.getId(), objectKey, "audio/wav");
        return new ListeningAudioObject(objectKey, bytes, "audio/wav");
    }

    private LevelTestItem completedOwnedItem(Long userId, Long itemId) {
        return itemRepository.findByIdAndSessionUserId(itemId, userId)
                .filter(item -> item.getSession().getStatus() == LevelTestSessionStatus.COMPLETED)
                .orElseThrow(() -> new BusinessException(
                        "완료된 Level Test 문항을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.LEVEL_TEST_NOT_FOUND
                ));
    }

    private String firstRecommendedAnswer(LevelTestItem item) {
        var response = responseRepository.findByItemId(item.getId()).orElse(null);
        if (response == null) {
            return null;
        }
        LevelTestEvaluation evaluation = evaluationRepository
                .findByResponseId(response.getId())
                .orElse(null);
        if (evaluation == null || evaluation.getRecommendedAnswersJson() == null) {
            return null;
        }
        List<String> answers = jsonCodec.read(
                evaluation.getRecommendedAnswersJson(),
                new TypeReference<List<String>>() {
                }
        );
        return answers == null || answers.isEmpty() ? null : answers.get(0);
    }

    private String sha256(String text) {
        try {
            String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).trim();
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(normalized.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
