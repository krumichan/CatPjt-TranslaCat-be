package jp.co.translacat.domain.languagelearning.level.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestPreviousEvaluationDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.WritingEvaluationScoresDto;
import jp.co.translacat.domain.languagelearning.common.enums.DailySetStatus;
import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingEvaluationRepository;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelStatusResponseDto;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestItemRepository;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestSessionRepository;
import jp.co.translacat.domain.languagelearning.profile.entity.LearningProfile;
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileQueryService;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningAdminSettingQueryService;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LevelTestQueryService {

    private final LevelTestSessionRepository sessionRepository;
    private final LevelTestItemRepository itemRepository;
    private final WritingEvaluationRepository evaluationRepository;
    private final DailyWritingSetRepository dailySetRepository;
    private final LearningProfileQueryService profileQueryService;
    private final LanguageLearningUserSettingQueryService userSettingQueryService;
    private final LanguageLearningAdminSettingQueryService adminSettingQueryService;

    @Transactional
    public LevelStatusResponseDto getStatus(Long userId) {
        LearningProfile profile = profileQueryService.getOrCreate(userId);
        Optional<LevelTestSession> activeSession = getActiveSession(userId);

        return new LevelStatusResponseDto(
                profile.getState(),
                hasCompletedInitialTest(userId),
                isRecheckRecommended(userId),
                activeSession.map(LevelTestSession::getId).orElse(null),
                activeSession.map(session -> nextQuestionNumber(
                        session.getId()
                )).orElse(null),
                profile.getBaseLevelScore()
        );
    }

    @Transactional(readOnly = true)
    public Optional<LevelTestSession> getActiveSession(Long userId) {
        return sessionRepository
                .findTopByUserIdAndStatusOrderByStartedAtDesc(
                        userId,
                        LevelTestSessionStatus.IN_PROGRESS
                );
    }

    @Transactional(readOnly = true)
    public LevelTestSession getOwnedSession(
            Long userId,
            Long sessionId
    ) {
        return sessionRepository.findById(sessionId)
                .filter(session -> session.getUser().getId().equals(userId))
                .orElseThrow(() -> new BusinessException(
                        "Level Test Session을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.LEVEL_TEST_NOT_FOUND
                ));
    }

    @Transactional(readOnly = true)
    public LevelTestItem getCurrentItemOrNull(Long sessionId) {
        int questionNumber = nextQuestionNumber(sessionId);
        return itemRepository
                .findBySessionIdAndQuestionNumber(
                        sessionId,
                        questionNumber
                )
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public int nextQuestionNumber(Long sessionId) {
        return previousEvaluations(sessionId).size() + 1;
    }

    @Transactional(readOnly = true)
    public List<LevelTestPreviousEvaluationDto> previousEvaluations(
            Long sessionId
    ) {
        List<LevelTestPreviousEvaluationDto> result = new ArrayList<>();

        for (LevelTestItem item :
                itemRepository.findAllBySessionIdOrderByQuestionNumberAsc(
                        sessionId
                )) {
            WritingEvaluation evaluation = evaluationRepository
                    .findByLevelTestItemId(item.getId())
                    .orElse(null);

            if (evaluation == null
                    || evaluation.getStatus() != EvaluationStatus.SUCCESS) {
                continue;
            }

            result.add(new LevelTestPreviousEvaluationDto(
                    item.getQuestionNumber(),
                    item.getDifficulty(),
                    toScores(evaluation)
            ));
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<WritingEvaluation> getSuccessfulEvaluations(
            Long sessionId
    ) {
        List<WritingEvaluation> result = new ArrayList<>();

        for (LevelTestItem item :
                itemRepository.findAllBySessionIdOrderByQuestionNumberAsc(
                        sessionId
                )) {
            evaluationRepository.findByLevelTestItemId(item.getId())
                    .filter(evaluation -> evaluation.getStatus()
                            == EvaluationStatus.SUCCESS)
                    .ifPresent(result::add);
        }

        return result;
    }

    @Transactional(readOnly = true)
    public boolean hasCompletedInitialTest(Long userId) {
        return sessionRepository
                .findTopByUserIdAndSessionTypeAndStatusOrderByCompletedAtDesc(
                        userId,
                        LevelTestSessionType.INITIAL,
                        LevelTestSessionStatus.COMPLETED
                )
                .isPresent();
    }

    @Transactional
    public boolean isRecheckRecommended(Long userId) {
        LanguageLearningUserSetting setting =
                userSettingQueryService.getOrCreateEntity(userId);
        LanguageLearningAdminSetting adminSetting =
                adminSettingQueryService.getOrCreateEntity();
        LocalDate today = userSettingQueryService.resolveToday(setting);

        DailyWritingSet latestCompleted = dailySetRepository
                .findTopByUserIdAndStatusOrderByLearningDateDesc(
                        userId,
                        DailySetStatus.COMPLETED
                )
                .orElse(null);

        if (latestCompleted == null) {
            return false;
        }

        long inactiveDays = ChronoUnit.DAYS.between(
                latestCompleted.getLearningDate(),
                today
        );
        return inactiveDays
                >= adminSetting.getLevelRecheckRecommendationDays();
    }

    private WritingEvaluationScoresDto toScores(
            WritingEvaluation evaluation
    ) {
        return new WritingEvaluationScoresDto(
                evaluation.getOverallScore(),
                evaluation.getMeaningScore(),
                evaluation.getGrammarScore(),
                evaluation.getVocabularyScore(),
                evaluation.getNaturalnessScore(),
                evaluation.getExpressionScore()
        );
    }
}
