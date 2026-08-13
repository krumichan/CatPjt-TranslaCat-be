package jp.co.translacat.domain.languagelearning.level.service;

import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionStatus;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingEvaluationRepository;
import jp.co.translacat.domain.languagelearning.daily.service.WritingEvaluationCommandService;
import jp.co.translacat.domain.languagelearning.level.dto.request.LevelAnswerRequestDto;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;
import jp.co.translacat.domain.languagelearning.level.model.LevelAnswerCommandResult;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestItemRepository;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningAdminSettingQueryService;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LevelTestAnswerCommandService {

    private final LevelTestItemRepository itemRepository;
    private final WritingEvaluationRepository evaluationRepository;
    private final LevelTestQueryService levelTestQueryService;
    private final LevelTestQuestionService questionService;
    private final WritingEvaluationCommandService evaluationCommandService;
    private final LevelTestCompletionCommandService completionCommandService;
    private final LanguageLearningUserSettingQueryService userSettingQueryService;
    private final LanguageLearningAdminSettingQueryService adminSettingQueryService;

    @Transactional(noRollbackFor = BusinessException.class)
    public LevelAnswerCommandResult submit(
            Long userId,
            Long sessionId,
            LevelAnswerRequestDto request
    ) {
        validateAiEvaluationEnabled();
        validateAnswer(request);

        LevelTestSession session = levelTestQueryService.getOwnedSession(
                userId,
                sessionId
        );
        validateInProgress(session);

        LanguageLearningUserSetting setting =
                userSettingQueryService.getOrCreateEntity(userId);
        int questionNumber = levelTestQueryService.nextQuestionNumber(
                sessionId
        );
        validateQuestionNumber(session, questionNumber);

        LevelTestItem item = getOrGenerateItem(
                session,
                questionNumber,
                setting
        );
        validateNotEvaluated(item);

        item.answer(request.answer().trim());
        WritingEvaluation evaluation = evaluationCommandService.evaluateLevel(
                session.getUser(),
                item,
                setting
        );

        if (questionNumber < session.getTotalQuestions()) {
            return inProgressResult(
                    sessionId,
                    questionNumber,
                    evaluation
            );
        }

        return completedResult(
                session,
                questionNumber,
                evaluation
        );
    }

    private LevelTestItem getOrGenerateItem(
            LevelTestSession session,
            int questionNumber,
            LanguageLearningUserSetting setting
    ) {
        return itemRepository
                .findBySessionIdAndQuestionNumber(
                        session.getId(),
                        questionNumber
                )
                .orElseGet(() -> questionService.generate(
                        session,
                        questionNumber,
                        setting
                ));
    }

    private LevelAnswerCommandResult inProgressResult(
            Long sessionId,
            int questionNumber,
            WritingEvaluation evaluation
    ) {
        return new LevelAnswerCommandResult(
                sessionId,
                questionNumber,
                evaluation,
                false,
                null
        );
    }

    private LevelAnswerCommandResult completedResult(
            LevelTestSession session,
            int questionNumber,
            WritingEvaluation evaluation
    ) {
        double baseLevelScore = completionCommandService.complete(session);

        return new LevelAnswerCommandResult(
                session.getId(),
                questionNumber,
                evaluation,
                true,
                baseLevelScore
        );
    }

    private void validateQuestionNumber(
            LevelTestSession session,
            int questionNumber
    ) {
        if (questionNumber > session.getTotalQuestions()) {
            throw new BusinessException(
                    "평가할 Level Test 문항이 없습니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }
    }

    private void validateNotEvaluated(LevelTestItem item) {
        WritingEvaluation existing = evaluationRepository
                .findByLevelTestItemId(item.getId())
                .orElse(null);

        if (existing != null
                && existing.getStatus() == EvaluationStatus.SUCCESS) {
            throw new BusinessException(
                    "이미 평가가 완료된 Level Test 문항입니다.",
                    LanguageLearningErrorCode.ANSWER_NOT_ALLOWED
            );
        }
    }

    private void validateInProgress(LevelTestSession session) {
        if (session.getStatus() != LevelTestSessionStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "진행 중인 Level Test가 아닙니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }
    }

    private void validateAnswer(LevelAnswerRequestDto request) {
        if (request == null
                || request.answer() == null
                || request.answer().isBlank()) {
            throw new BusinessException(
                    "답변이 필요합니다.",
                    LanguageLearningErrorCode.ANSWER_NOT_ALLOWED
            );
        }
    }

    private void validateAiEvaluationEnabled() {
        if (!adminSettingQueryService
                .getOrCreateEntity()
                .isAiEvaluationEnabled()) {
            throw new BusinessException(
                    "AI Writing 평가가 비활성화되어 있습니다.",
                    LanguageLearningErrorCode.SETTING_INVALID
            );
        }
    }
}
