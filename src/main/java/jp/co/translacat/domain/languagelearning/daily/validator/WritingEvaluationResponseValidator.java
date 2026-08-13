package jp.co.translacat.domain.languagelearning.daily.validator;

import jp.co.translacat.domain.languagelearning.ai.dto.response.AiWritingEvaluationResponseDto;

import org.springframework.stereotype.Component;

@Component
public class WritingEvaluationResponseValidator {

    public void validate(AiWritingEvaluationResponseDto response) {
        boolean invalid = response == null
                || response.scores() == null
                || response.recommendedAnswers() == null
                || response.recommendedAnswers().size() < 2
                || response.recommendedAnswers().size() > 3
                || response.evaluationRubricVersion() == null
                || response.scoringPolicyVersion() == null;

        if (invalid) {
            throw new IllegalStateException(
                    "AI 평가 Contract가 유효하지 않습니다."
            );
        }
    }
}
