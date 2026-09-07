package jp.co.translacat.domain.languagelearning.daily.validator;

import jp.co.translacat.domain.languagelearning.ai.dto.model.WritingEvaluationScoresDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiWritingEvaluationResponseDto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WritingEvaluationResponseValidatorTest {

    private final WritingEvaluationResponseValidator validator =
            new WritingEvaluationResponseValidator();

    @Test
    void acceptsTwoRecommendedAnswersAndPolicyVersions() {
        AiWritingEvaluationResponseDto response = response(
                List.of("answer-1", "answer-2"),
                "writing-evaluation-rubric",
                "writing-scoring-policy"
        );

        assertThatCode(() -> validator.validate(response))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsSingleRecommendedAnswer() {
        AiWritingEvaluationResponseDto response = response(
                List.of("answer-1"),
                "writing-evaluation-rubric",
                "writing-scoring-policy"
        );

        assertThatThrownBy(() -> validator.validate(response))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMissingPolicyVersion() {
        AiWritingEvaluationResponseDto response = response(
                List.of("answer-1", "answer-2"),
                null,
                "writing-scoring-policy"
        );

        assertThatThrownBy(() -> validator.validate(response))
                .isInstanceOf(IllegalStateException.class);
    }

    private AiWritingEvaluationResponseDto response(
            List<String> recommendedAnswers,
            String rubricVersion,
            String scoringPolicyVersion
    ) {
        return new AiWritingEvaluationResponseDto(
                "eval-1",
                new WritingEvaluationScoresDto(
                        80,
                        80,
                        80,
                        80,
                        80,
                        80
                ),
                List.of(),
                List.of(),
                List.of(),
                recommendedAnswers,
                null,
                null,
                rubricVersion,
                scoringPolicyVersion,
                "current-prompt"
        );
    }
}
