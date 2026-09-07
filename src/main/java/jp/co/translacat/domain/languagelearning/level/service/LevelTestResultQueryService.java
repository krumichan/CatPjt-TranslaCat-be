package jp.co.translacat.domain.languagelearning.level.service;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestInternalAnswerKeyDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestOptionDto;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionStatus;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestDomainScoresResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestHistoryDetailResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestHistoryItemResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestItemDetailResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestOptionResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestTaskGuidanceResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestResultResponseDto;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestEvaluation;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestResponse;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestEvaluationRepository;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestItemRepository;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestResponseRepository;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestSessionRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LevelTestResultQueryService {

    private final LevelTestSessionRepository sessionRepository;
    private final LevelTestItemRepository itemRepository;
    private final LevelTestResponseRepository responseRepository;
    private final LevelTestEvaluationRepository evaluationRepository;
    private final LevelTestProgressCommandService progressCommandService;
    private final LanguageLearningJsonCodec jsonCodec;

    public LevelTestResultResponseDto getResult(
            Long userId,
            Long sessionId
    ) {
        LevelTestSession session = getCompletedSession(userId, sessionId);
        Map<LevelTestDomain, Integer> scores =
                progressCommandService.calculateDomainScores(sessionId);

        return new LevelTestResultResponseDto(
                session.getId(),
                session.getSessionType(),
                session.getBaseLevelScore() == null
                        ? null
                        : (int) Math.round(session.getBaseLevelScore()),
                session.getProficiencyBand(),
                scores == null ? null : toDomainScores(scores),
                "MY_LEVEL",
                session.getCompletedAt()
        );
    }

    public List<LevelTestHistoryItemResponseDto> getHistory(Long userId) {
        return sessionRepository
                .findAllByUserIdAndStatusOrderByCompletedAtDesc(
                        userId,
                        LevelTestSessionStatus.COMPLETED
                )
                .stream()
                .map(this::toHistoryItem)
                .sorted(Comparator.comparing(
                        LevelTestHistoryItemResponseDto::completedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    public LevelTestHistoryDetailResponseDto getHistoryDetail(
            Long userId,
            Long sessionId
    ) {
        LevelTestSession session = getCompletedSession(userId, sessionId);
        return new LevelTestHistoryDetailResponseDto(
                toHistoryItem(session),
                itemRepository
                        .findAllBySessionIdOrderByQuestionNumberAsc(sessionId)
                        .stream()
                        .map(this::toItemDetail)
                        .toList()
        );
    }

    public LevelTestHistoryDetailResponseDto historyDetailForActivity(
            Long userId,
            Long sessionId
    ) {
        return getHistoryDetail(userId, sessionId);
    }

    private LevelTestHistoryItemResponseDto toHistoryItem(
            LevelTestSession session
    ) {
        Map<LevelTestDomain, Integer> scores =
                progressCommandService.calculateDomainScores(session.getId());
        return new LevelTestHistoryItemResponseDto(
                session.getId(),
                session.getSessionType(),
                session.getBaseLevelScore() == null
                        ? null
                        : (int) Math.round(session.getBaseLevelScore()),
                session.getProficiencyBand(),
                scores == null ? null : toDomainScores(scores),
                session.getCompletedAt()
        );
    }

    private LevelTestItemDetailResponseDto toItemDetail(LevelTestItem item) {
        LevelTestResponse response = responseRepository
                .findByItemId(item.getId())
                .orElse(null);
        LevelTestEvaluation evaluation = response == null
                ? null
                : evaluationRepository.findByResponseId(response.getId())
                        .orElse(null);

        List<LevelTestOptionResponseDto> options = readOptions(item);
        Map<String, Object> referencePayload = readReferencePayload(
                item.getReferencePayloadJson()
        );
        LevelTestInternalAnswerKeyDto answerKey = readAnswerKey(
                item.getInternalAnswerKeyJson()
        );
        List<String> selectedOptionKeys = response == null
                || response.getSelectedOptionKeysJson() == null
                ? List.of()
                : jsonCodec.read(
                        response.getSelectedOptionKeysJson(),
                        new TypeReference<List<String>>() {
                        }
                );
        List<Map<String, Object>> metrics = evaluation == null
                ? List.of()
                : jsonCodec.read(
                        evaluation.getMetricsJson(),
                        new TypeReference<List<Map<String, Object>>>() {
                        }
                );
        List<String> strengths = evaluation == null
                ? List.of()
                : jsonCodec.read(
                        evaluation.getStrengthsJson(),
                        new TypeReference<List<String>>() {
                        }
                );
        List<String> improvements = evaluation == null
                ? List.of()
                : jsonCodec.read(
                        evaluation.getImprovementsJson(),
                        new TypeReference<List<String>>() {
                        }
                );

        List<String> recommendedAnswers = evaluation == null
                || evaluation.getRecommendedAnswersJson() == null
                ? List.of()
                : jsonCodec.read(
                        evaluation.getRecommendedAnswersJson(),
                        new TypeReference<List<String>>() {
                        }
                );
        List<Map<String, Object>> detailedFeedback = evaluation == null
                || evaluation.getDetailedFeedbackJson() == null
                ? List.of()
                : jsonCodec.read(
                        evaluation.getDetailedFeedbackJson(),
                        new TypeReference<List<Map<String, Object>>>() {
                        }
                );
        boolean modelAnswerAudioAvailable = item.getItemType() != null
                && item.getItemType().name().startsWith("SPEAKING_")
                && (item.getItemType().name().equals("SPEAKING_REPEAT")
                ? item.getReferenceAudioObjectKey() != null
                : !recommendedAnswers.isEmpty());
        boolean answerAudioAvailable = response != null
                && response.getAudioObjectKey() != null
                && response.getAudioDeletedAt() == null
                && (response.getAudioRetentionUntil() == null
                || !response.getAudioRetentionUntil().isBefore(LocalDateTime.now()));

        return new LevelTestItemDetailResponseDto(
                item.getId(),
                item.getQuestionNumber(),
                item.getDomain(),
                item.getItemType(),
                item.getComplexityBandValue(),
                item.getInstruction(),
                item.getPromptText(),
                options,
                stringValue(referencePayload.get("emphasisText")),
                taskGuidance(referencePayload),
                response == null ? null : response.getSelectedOptionKey(),
                selectedOptionKeys,
                response == null ? null : response.getTextAnswer(),
                response != null && response.getAudioObjectKey() != null,
                answerAudioAvailable,
                item.getReferenceAudioObjectKey() != null,
                evaluation == null ? null : evaluation.getTranscript(),
                recommendedAnswers,
                detailedFeedback,
                modelAnswerAudioAvailable,
                answerKey == null ? null : answerKey.correctOptionKey(),
                answerKey == null || answerKey.correctOrder() == null
                        ? List.of()
                        : answerKey.correctOrder(),
                evaluation != null && evaluation.isEvaluable(),
                evaluation == null ? null : evaluation.getScore(),
                evaluation == null ? null : evaluation.getConfidence(),
                metrics,
                strengths,
                improvements,
                evaluation == null ? null : evaluation.getReasonCode()
        );
    }

    private List<LevelTestOptionResponseDto> readOptions(LevelTestItem item) {
        if (item.getOptionsJson() == null || item.getOptionsJson().isBlank()) {
            return List.of();
        }
        List<LevelTestOptionDto> values = jsonCodec.read(
                item.getOptionsJson(),
                new TypeReference<List<LevelTestOptionDto>>() {
                }
        );
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null)
                .map(value -> new LevelTestOptionResponseDto(
                        value.key(),
                        value.text()
                ))
                .toList();
    }

    private LevelTestInternalAnswerKeyDto readAnswerKey(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return jsonCodec.read(json, LevelTestInternalAnswerKeyDto.class);
    }

    private Map<String, Object> readReferencePayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return jsonCodec.read(json, new TypeReference<Map<String, Object>>() {
        });
    }

    private LevelTestTaskGuidanceResponseDto taskGuidance(
            Map<String, Object> payload
    ) {
        List<String> facts = stringList(payload.get("providedFacts"));
        List<String> intents = stringList(payload.get("requiredIntents"));
        List<String> constraints = stringList(payload.get("responseConstraints"));
        if (facts.isEmpty() && intents.isEmpty() && constraints.isEmpty()) {
            return null;
        }
        return new LevelTestTaskGuidanceResponseDto(
                facts,
                intents,
                constraints
        );
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(entry -> !entry.isBlank())
                .toList();
    }

    private String stringValue(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text;
    }

    private LevelTestSession getCompletedSession(
            Long userId,
            Long sessionId
    ) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .filter(value -> value.getStatus()
                        == LevelTestSessionStatus.COMPLETED)
                .orElseThrow(this::notFound);
    }

    private LevelTestDomainScoresResponseDto toDomainScores(
            Map<LevelTestDomain, Integer> scores
    ) {
        return new LevelTestDomainScoresResponseDto(
                scores.get(LevelTestDomain.VOCABULARY),
                scores.get(LevelTestDomain.GRAMMAR),
                scores.get(LevelTestDomain.READING),
                scores.get(LevelTestDomain.LISTENING),
                scores.get(LevelTestDomain.WRITING),
                scores.get(LevelTestDomain.SPEAKING)
        );
    }

    private BusinessException notFound() {
        return new BusinessException(
                "Level Test 결과를 찾을 수 없습니다.",
                LanguageLearningErrorCode.LEVEL_TEST_NOT_FOUND
        );
    }
}
