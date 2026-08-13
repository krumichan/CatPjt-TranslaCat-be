package jp.co.translacat.domain.languagelearning.level.facade;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;
import jp.co.translacat.domain.languagelearning.daily.mapper.WritingEvaluationResponseMapper;
import jp.co.translacat.domain.languagelearning.level.dto.request.LevelAnswerRequestDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelAnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelQuestionResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelStatusResponseDto;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;
import jp.co.translacat.domain.languagelearning.level.model.LevelAnswerCommandResult;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestAnswerCommandService;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestQueryService;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestQuestionService;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestSessionCommandService;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LanguageLearningLevelTestFacade {

    private final LevelTestQueryService levelTestQueryService;
    private final LevelTestSessionCommandService sessionCommandService;
    private final LevelTestAnswerCommandService answerCommandService;
    private final LevelTestQuestionService questionService;
    private final LanguageLearningUserSettingQueryService userSettingQueryService;
    private final WritingEvaluationResponseMapper evaluationResponseMapper;

    public LevelStatusResponseDto getStatus(Long userId) {
        return levelTestQueryService.getStatus(userId);
    }

    public LevelQuestionResponseDto start(
            Long userId,
            LevelTestSessionType sessionType
    ) {
        LevelTestSession session = sessionCommandService.start(
                userId,
                sessionType
        );

        return getOrGenerateCurrent(userId, session);
    }

    public LevelQuestionResponseDto getCurrent(
            Long userId,
            Long sessionId
    ) {
        LevelTestSession session = levelTestQueryService.getOwnedSession(
                userId,
                sessionId
        );

        return getOrGenerateCurrent(userId, session);
    }

    public LevelAnswerResultResponseDto submit(
            Long userId,
            Long sessionId,
            LevelAnswerRequestDto request
    ) {
        LevelAnswerCommandResult result = answerCommandService.submit(
                userId,
                sessionId,
                request
        );
        LevelQuestionResponseDto nextQuestion = result.completed()
                ? null
                : getCurrent(userId, sessionId);

        return new LevelAnswerResultResponseDto(
                result.sessionId(),
                result.questionNumber(),
                evaluationResponseMapper.toResponse(result.evaluation()),
                result.completed(),
                result.baseLevelScore(),
                nextQuestion
        );
    }

    private LevelQuestionResponseDto getOrGenerateCurrent(
            Long userId,
            LevelTestSession session
    ) {
        LanguageLearningUserSetting setting =
                userSettingQueryService.getOrCreateEntity(userId);
        LevelTestItem item = questionService.getOrGenerateCurrent(
                session,
                setting
        );

        return questionService.toResponse(item);
    }
}
