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
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileCommandService;
import jp.co.translacat.domain.languagelearning.resultjournal.model.LearningResultCaptured;
import jp.co.translacat.domain.languagelearning.resultjournal.model.WritingResultFact;
import jp.co.translacat.domain.languagelearning.setting.model.UserSettingsSnapshot;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher resultEvents;

    @Transactional(noRollbackFor = BusinessException.class)
    public WritingEvaluation evaluateDaily(
            User user,
            WritingAnswer answer,
            UserSettingsSnapshot setting,
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

            // BEFORE_COMMIT 수신기가 같은 Core 트랜잭션에 outbox를 기록한다. 여기서 HTTP는 호출하지 않는다.
            resultEvents.publishEvent(new LearningResultCaptured(
                    user.getId(), "WRITING_SCORED", answer.getId().toString(),
                    jsonCodec.write(new WritingResultFact(
                            "SCORED_EVALUATION", evaluation.getId(), answer.getId(), answer.getDailyItem().getId(),
                            learningDate.toString(), setting.getOriginLanguage(), setting.getLearningLanguage(),
                            answer.getDailyItem().getDifficulty().name(), response, requestContext.relevantKeywords()
                    ))
            ));
            return evaluation;
        } catch (Exception e) {
            persistFailure(evaluation, e);
            throw evaluationFailure("AI Writing 평가에 실패했습니다.");
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
