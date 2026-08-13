package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.ai.dto.response.AiWritingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.port.LanguageLearningAiClient;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingAnswer;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;
import jp.co.translacat.domain.languagelearning.daily.factory.WritingEvaluationRequestFactory;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingSnapshot;
import jp.co.translacat.domain.languagelearning.daily.model.WritingEvaluationRequestContext;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingEvaluationRepository;
import jp.co.translacat.domain.languagelearning.daily.validator.WritingEvaluationResponseValidator;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileCommandService;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class WritingEvaluationCommandService {

    private final LanguageLearningAiClient aiClient;
    private final WritingEvaluationRepository evaluationRepository;
    private final LearningProfileCommandService profileCommandService;
    private final WritingEvaluationRequestFactory requestFactory;
    private final WritingEvaluationResponseValidator responseValidator;
    private final LanguageLearningJsonCodec jsonCodec;

    @Transactional(noRollbackFor = BusinessException.class)
    public WritingEvaluation evaluateDaily(
            User user,
            WritingAnswer answer,
            LanguageLearningUserSetting setting,
            DailyWritingSnapshot snapshot,
            LocalDate learningDate
    ) {
        WritingEvaluation evaluation = getOrCreateDailyEvaluation(
                user,
                answer
        );
        WritingEvaluationRequestContext requestContext =
                requestFactory.createDaily(
                        answer,
                        setting,
                        snapshot,
                        learningDate
                );

        try {
            AiWritingEvaluationResponseDto response = aiClient.evaluate(
                    requestContext.request()
            );
            responseValidator.validate(response);
            persistSuccess(evaluation, response);

            profileCommandService.applyDailyEvaluation(
                    user.getId(),
                    response,
                    answer.getDailyItem().getDifficulty(),
                    requestContext.relevantKeywords(),
                    learningDate
            );

            return evaluation;
        } catch (Exception e) {
            persistFailure(evaluation, e);
            throw evaluationFailure("AI Writing 평가에 실패했습니다.");
        }
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public WritingEvaluation evaluateLevel(
            User user,
            LevelTestItem item,
            LanguageLearningUserSetting setting
    ) {
        WritingEvaluation evaluation = getOrCreateLevelEvaluation(
                user,
                item
        );

        try {
            AiWritingEvaluationResponseDto response = aiClient.evaluate(
                    requestFactory.createLevel(
                            user.getId(),
                            item,
                            setting
                    )
            );
            responseValidator.validate(response);
            persistSuccess(evaluation, response);

            return evaluation;
        } catch (Exception e) {
            persistFailure(evaluation, e);
            throw evaluationFailure("AI Level Test 평가에 실패했습니다.");
        }
    }

    private WritingEvaluation getOrCreateDailyEvaluation(
            User user,
            WritingAnswer answer
    ) {
        return evaluationRepository.findByAnswerId(answer.getId())
                .orElseGet(() -> evaluationRepository.save(
                        WritingEvaluation.pendingDaily(user, answer)
                ));
    }

    private WritingEvaluation getOrCreateLevelEvaluation(
            User user,
            LevelTestItem item
    ) {
        return evaluationRepository.findByLevelTestItemId(item.getId())
                .orElseGet(() -> evaluationRepository.save(
                        WritingEvaluation.pendingLevel(user, item)
                ));
    }

    private void persistSuccess(
            WritingEvaluation evaluation,
            AiWritingEvaluationResponseDto response
    ) {
        var scores = response.scores();

        evaluation.success(
                scores.overall(),
                scores.meaning(),
                scores.grammar(),
                scores.vocabulary(),
                scores.naturalness(),
                scores.expression(),
                jsonCodec.write(response.strengths()),
                jsonCodec.write(response.weaknesses()),
                jsonCodec.write(response.corrections()),
                jsonCodec.write(response.recommendedAnswers()),
                jsonCodec.write(response.explanation()),
                jsonCodec.write(response.profileSignals()),
                response.evaluationRubricVersion(),
                response.scoringPolicyVersion(),
                response.promptVersion()
        );
        evaluationRepository.save(evaluation);
    }

    private void persistFailure(
            WritingEvaluation evaluation,
            Exception exception
    ) {
        evaluation.fail(trimMessage(exception.getMessage()));
        evaluationRepository.save(evaluation);
    }

    private BusinessException evaluationFailure(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.EVALUATION_FAILED
        );
    }

    private String trimMessage(String message) {
        if (message == null) {
            return "unknown";
        }

        return message.length() <= 1000
                ? message
                : message.substring(0, 1000);
    }
}
