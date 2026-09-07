package jp.co.translacat.domain.languagelearning.level.service;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestInternalAnswerKeyDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiLevelTestSpeakingEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiLevelTestTextEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiLevelTestEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.port.LanguageLearningAiClient;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestAnswerMode;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionStatus;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestEvaluation;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestResponse;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestEvaluationRepository;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestResponseRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LevelTestEvaluationService {

    private static final Set<String> RERECORD_REQUIRED_REASON_CODES = Set.of(
            "INVALID_AUDIO",
            "SILENCE_DETECTED",
            "UNSUPPORTED_AUDIO_FORMAT",
            "AUDIO_TOO_SHORT",
            "AUDIO_TOO_LONG",
            "LOW_STT_CONFIDENCE"
    );

    private final LanguageLearningAiClient aiClient;
    private final LevelTestResponseRepository responseRepository;
    private final LevelTestEvaluationRepository evaluationRepository;
    private final LevelTestAnswerCommandService answerCommandService;
    private final LevelTestProgressCommandService progressCommandService;
    private final LevelTestEvaluationStateService stateService;
    private final LevelTestAudioService audioService;
    private final LanguageLearningJsonCodec jsonCodec;

    public LevelTestProgressCommandService.ProgressResult evaluate(
            LevelTestResponse response,
            byte[] audio,
            String fileName,
            String contentType
    ) {
        LevelTestItem item = response.getItem();
        LevelTestEvaluation existing = evaluationRepository
                .findByResponseId(response.getId())
                .orElse(null);
        if (existing != null && isEvaluationFinished(item)) {
            return existingProgress(item, existing);
        }

        try {
            LevelTestProgressCommandService.EvaluationPayload payload =
                    switch (item.getAnswerMode()) {
                        case CHOICE -> objective(item, response);
                        case TEXT -> text(item, response);
                        case AUDIO -> speaking(
                                item,
                                response,
                                audio,
                                fileName,
                                contentType
                        );
                    };
            return progressCommandService.apply(response.getId(), payload);
        } catch (RuntimeException exception) {
            stateService.markFailed(item.getId());
            throw exception;
        }
    }

    public LevelTestProgressCommandService.ProgressResult replay(
            LevelTestResponse response
    ) {
        LevelTestItem item = response.getItem();
        LevelTestEvaluation existing = evaluationRepository
                .findByResponseId(response.getId())
                .orElse(null);
        if (item.getStatus() == LevelTestItemStatus.EVALUATION_FAILED
                && existing == null) {
            return new LevelTestProgressCommandService.ProgressResult(
                    false,
                    null,
                    LanguageLearningErrorCode.LEVEL_TEST_EVALUATION_FAILED,
                    false
            );
        }
        if (existing == null || !isEvaluationFinished(item)) {
            throw new BusinessException(
                    "동일 요청의 Level Test 평가가 아직 진행 중입니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }
        return existingProgress(item, existing);
    }

    public LevelTestProgressCommandService.ProgressResult retry(
            Long userId,
            Long sessionId,
            Long itemId
    ) {
        LevelTestResponse response = responseRepository.findByItemId(itemId)
                .filter(value -> value.getItem().getSession().getId()
                        .equals(sessionId))
                .filter(value -> value.getItem().getSession().getUser().getId()
                        .equals(userId))
                .orElseThrow(() -> new BusinessException(
                        "재평가할 Level Test 답변을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.LEVEL_TEST_NOT_FOUND
                ));
        LevelTestEvaluation existingEvaluation = evaluationRepository
                .findByResponseId(response.getId())
                .orElse(null);
        if (response.getItem().getAnswerMode() == LevelTestAnswerMode.AUDIO
                && existingEvaluation != null
                && RERECORD_REQUIRED_REASON_CODES.contains(existingEvaluation.getReasonCode())) {
            throw new BusinessException(
                    "음성을 인식할 수 없는 답변은 평가 재시도가 아니라 다시 녹음해야 합니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_AUDIO_INVALID
            );
        }

        answerCommandService.registerRetry(response.getId());

        if (response.getItem().getAnswerMode() == LevelTestAnswerMode.AUDIO) {
            return evaluate(
                    response,
                    audioService.load(response),
                    "level-test-retry.bin",
                    response.getAudioContentType()
            );
        }
        return evaluate(response, null, null, null);
    }

    private boolean isEvaluationFinished(LevelTestItem item) {
        return item.getStatus() == LevelTestItemStatus.EVALUATED
                || item.getStatus() == LevelTestItemStatus.EVALUATION_FAILED;
    }

    private LevelTestProgressCommandService.ProgressResult existingProgress(
            LevelTestItem item,
            LevelTestEvaluation evaluation
    ) {
        return new LevelTestProgressCommandService.ProgressResult(
                evaluation.isEvaluable(),
                evaluation.getScore(),
                evaluation.getReasonCode(),
                item.getSession().getStatus()
                        == LevelTestSessionStatus.COMPLETED
        );
    }

    private LevelTestProgressCommandService.EvaluationPayload objective(
            LevelTestItem item,
            LevelTestResponse response
    ) {
        LevelTestInternalAnswerKeyDto answerKey = jsonCodec.read(
                item.getInternalAnswerKeyJson(),
                LevelTestInternalAnswerKeyDto.class
        );
        int score;
        if (answerKey.correctOrder() != null
                && !answerKey.correctOrder().isEmpty()) {
            List<String> submitted = jsonCodec.read(
                    response.getSelectedOptionKeysJson(),
                    new TypeReference<List<String>>() {
                    }
            );
            score = answerKey.correctOrder().equals(submitted) ? 100 : 0;
        } else {
            score = objectiveChoiceScore(answerKey, response.getSelectedOptionKey());
        }
        return new LevelTestProgressCommandService.EvaluationPayload(
                true,
                score,
                1.0,
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                "LEVEL_TEST_OBJECTIVE"
        );
    }

    private int objectiveChoiceScore(
            LevelTestInternalAnswerKeyDto answerKey,
            String selectedOptionKey
    ) {
        Map<String, Integer> optionScores = answerKey.optionScores();
        if (optionScores == null || optionScores.isEmpty()) {
            return Objects.equals(answerKey.correctOptionKey(), selectedOptionKey)
                    ? 100
                    : 0;
        }
        boolean invalidScore = optionScores.entrySet().stream()
                .anyMatch(entry -> entry.getKey() == null
                        || entry.getKey().isBlank()
                        || entry.getValue() == null
                        || entry.getValue() < 0
                        || entry.getValue() > 100);
        if (invalidScore
                || !Objects.equals(optionScores.get(answerKey.correctOptionKey()), 100)
                || !optionScores.containsKey(selectedOptionKey)) {
            throw new BusinessException(
                    "Level Test 객관식 부분점수 계약이 유효하지 않습니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }
        return optionScores.get(selectedOptionKey);
    }

    private LevelTestProgressCommandService.EvaluationPayload text(
            LevelTestItem item,
            LevelTestResponse response
    ) {
        Map<String, Object> reference = referencePayload(item);
        AiLevelTestTextEvaluationRequestDto request =
                new AiLevelTestTextEvaluationRequestDto(
                        "level-eval-"
                                + response.getId()
                                + "-"
                                + response.getManualEvaluationRetryCount(),
                        "level:eval:"
                                + response.getId()
                                + ":"
                                + response.getManualEvaluationRetryCount(),
                        item.getSession().getId(),
                        item.getId(),
                        item.getDomain(),
                        item.getItemType(),
                        item.getPromptText(),
                        response.getTextAnswer(),
                        item.getSession().getOriginLanguage(),
                        item.getSession().getLearningLanguage(),
                        item.getComplexityBandValue(),
                        string(reference.get("sourceText")),
                        strings(reference.get("referenceMeanings")),
                        strings(reference.get("keyMeaningUnits")),
                        string(reference.get("translationSourceText")),
                        strings(reference.get("providedFacts")),
                        strings(reference.get("requiredIntents")),
                        strings(reference.get("responseConstraints")),
                        List.of(),
                        response.getManualEvaluationRetryCount()
                );
        return fromAi(
                item,
                aiClient.evaluateLevelTestText(request)
        );
    }

    private LevelTestProgressCommandService.EvaluationPayload speaking(
            LevelTestItem item,
            LevelTestResponse response,
            byte[] audio,
            String fileName,
            String contentType
    ) {
        if (audio == null || audio.length == 0) {
            throw new BusinessException(
                    "Level Test Speaking Audio가 없습니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_AUDIO_INVALID
            );
        }

        Map<String, Object> reference = referencePayload(item);
        AiLevelTestSpeakingEvaluationRequestDto request =
                new AiLevelTestSpeakingEvaluationRequestDto(
                        "level-speaking-"
                                + response.getId()
                                + "-"
                                + response.getManualEvaluationRetryCount(),
                        "level:speaking:"
                                + response.getId()
                                + ":"
                                + response.getManualEvaluationRetryCount(),
                        item.getSession().getId(),
                        item.getId(),
                        item.getItemType(),
                        item.getPromptText(),
                        string(reference.get("referenceText")),
                        item.getSession().getOriginLanguage(),
                        item.getSession().getLearningLanguage(),
                        item.getComplexityBandValue(),
                        Math.max(
                                3,
                                item.getMaxAudioSeconds() == null
                                        ? 30
                                        : item.getMaxAudioSeconds()
                        ),
                        strings(reference.get("phraseHints")),
                        strings(reference.get("providedFacts")),
                        strings(reference.get("requiredIntents")),
                        strings(reference.get("responseConstraints")),
                        response.getManualEvaluationRetryCount()
                );
        return fromAi(
                item,
                aiClient.evaluateLevelTestSpeaking(
                        request,
                        audio,
                        fileName,
                        contentType
                )
        );
    }

    private LevelTestProgressCommandService.EvaluationPayload fromAi(
            LevelTestItem item,
            AiLevelTestEvaluationResponseDto response
    ) {
        if (response == null
                || response.sessionId() == null
                || !response.sessionId().equals(item.getSession().getId())
                || response.itemId() == null
                || !response.itemId().equals(item.getId())
                || response.domain() != item.getDomain()
                || response.itemType() != item.getItemType()
                || response.confidence() != null
                && (response.confidence() < 0
                || response.confidence() > 1)
                || response.score() != null
                && (response.score() < 0 || response.score() > 100)
                || response.evaluable() && response.score() == null) {
            throw new BusinessException(
                    "AI Level Test 평가 계약이 유효하지 않습니다.",
                    LanguageLearningErrorCode.AI_SCHEMA_INVALID
            );
        }
        return new LevelTestProgressCommandService.EvaluationPayload(
                response.evaluable(),
                response.score(),
                response.confidence(),
                response.metrics(),
                response.strengths(),
                response.improvements(),
                response.transcript(),
                response.recommendedAnswers(),
                response.detailedFeedback(),
                response.assessmentSignals(),
                response.reasonCode(),
                response.evaluationVersion()
        );
    }

    private Map<String, Object> referencePayload(LevelTestItem item) {
        return jsonCodec.read(
                item.getReferencePayloadJson(),
                new TypeReference<Map<String, Object>>() {
                }
        );
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }
}
