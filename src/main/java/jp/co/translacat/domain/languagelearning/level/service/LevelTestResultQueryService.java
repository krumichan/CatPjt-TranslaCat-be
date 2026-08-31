package jp.co.translacat.domain.languagelearning.level.service;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionStatus;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestDomainScoresResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestHistoryDetailResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestHistoryItemResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestItemDetailResponseDto;
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
        Map<LevelTestDomain, Integer> scores = session.isMultiSkill()
                ? progressCommandService.calculateDomainScores(sessionId)
                : null;

        return new LevelTestResultResponseDto(
                session.getId(),
                session.effectiveAssessmentVersion(),
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
                session.isMultiSkill()
                        ? itemRepository
                                .findAllBySessionIdOrderByQuestionNumberAsc(sessionId)
                                .stream()
                                .map(this::toItemDetail)
                                .toList()
                        : List.of()
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
        Map<LevelTestDomain, Integer> scores = session.isMultiSkill()
                ? progressCommandService.calculateDomainScores(session.getId())
                : null;
        return new LevelTestHistoryItemResponseDto(
                session.getId(),
                session.effectiveAssessmentVersion(),
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

        return new LevelTestItemDetailResponseDto(
                item.getQuestionNumber(),
                item.getDomain(),
                item.getItemType(),
                item.getComplexityBandValue(),
                item.getPromptText(),
                response == null ? null : response.getSelectedOptionKey(),
                selectedOptionKeys,
                response == null ? null : response.getTextAnswer(),
                response != null && response.getAudioObjectKey() != null,
                evaluation != null && evaluation.isEvaluable(),
                evaluation == null ? null : evaluation.getScore(),
                evaluation == null ? null : evaluation.getConfidence(),
                metrics,
                strengths,
                improvements,
                evaluation == null ? null : evaluation.getReasonCode()
        );
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
