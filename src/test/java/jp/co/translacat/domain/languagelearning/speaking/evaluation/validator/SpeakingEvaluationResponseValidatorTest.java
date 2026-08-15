package jp.co.translacat.domain.languagelearning.speaking.evaluation.validator;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingMetricDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingMetricType;
import jp.co.translacat.global.exception.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SpeakingEvaluationResponseValidatorTest {

    private final SpeakingEvaluationResponseValidator validator =
            new SpeakingEvaluationResponseValidator();

    @Test
    void acceptsEightEvaluatedMetrics() {
        assertThatCode(() -> validator.validate(response(metrics(), 80), List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsNotEvaluableMetricWithoutScore() {
        List<AiSpeakingMetricDto> metrics = metrics();
        metrics.set(6, new AiSpeakingMetricDto(
                SpeakingMetricType.PRONUNCIATION,
                "NOT_EVALUABLE",
                null,
                0.8,
                "Audio quality was insufficient.",
                List.of(),
                "LOW_AUDIO_QUALITY"
        ));

        assertThatCode(() -> validator.validate(response(metrics, 78), List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingMetric() {
        List<AiSpeakingMetricDto> metrics = metrics().subList(0, 7);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> validator.validate(response(metrics, 80), List.of()));
    }

    @Test
    void rejectsEvaluatedResponseWithoutOverallScore() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> validator.validate(response(metrics(), null), List.of()));
    }

    private List<AiSpeakingMetricDto> metrics() {
        return new java.util.ArrayList<>(Arrays.stream(SpeakingMetricType.values())
                .map(type -> new AiSpeakingMetricDto(
                        type,
                        "EVALUATED",
                        80.0,
                        0.9,
                        "ok",
                        List.of(),
                        null
                ))
                .toList());
    }

    private AiSpeakingEvaluationResponseDto response(
            List<AiSpeakingMetricDto> metrics,
            Integer overall
    ) {
        return new AiSpeakingEvaluationResponseDto(
                "request",
                "session",
                "EVALUATED",
                overall,
                0.9,
                metrics,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                "speaking-evaluation-policy-v1",
                "speaking-scoring-policy-v1",
                "prompt-v1",
                null
        );
    }
}
