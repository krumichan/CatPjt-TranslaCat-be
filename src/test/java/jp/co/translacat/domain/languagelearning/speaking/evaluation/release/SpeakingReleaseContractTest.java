package jp.co.translacat.domain.languagelearning.speaking.evaluation.release;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.validator.SpeakingEvaluationResponseValidator;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static jp.co.translacat.domain.languagelearning.speaking.evaluation.release.SpeakingReleaseFixtures.*;

class SpeakingReleaseContractTest {
    private final SpeakingEvaluationResponseValidator validator = new SpeakingEvaluationResponseValidator();
    @ParameterizedTest
    @ValueSource(strings = {"evaluated", "precheck-insufficient", "model-insufficient", "all-metrics-not-evaluable"})
    void acceptsActualAiResponsesAfterJacksonDeserialization(String name) {
        assertThatCode(() -> validator.validate(response(name), request(name))).doesNotThrowAnyException();
    }
    @ParameterizedTest
    @ValueSource(strings = {"requestId", "sessionId"})
    void rejectsResponseForAnotherRequestOrSession(String field) throws Exception {
        ObjectNode changed = (ObjectNode) fixture("evaluated").get("response");
        changed.put(field, "other");
        var response = JSON.treeToValue(changed, AiSpeakingEvaluationResponseDto.class);
        assertThatThrownBy(() -> validator.validate(response, request("evaluated"))).isInstanceOf(BusinessException.class);
    }
    @Test
    void rejectsInsufficientEvidenceCarryingAnOverallScore() throws Exception {
        ObjectNode changed = (ObjectNode) fixture("precheck-insufficient").get("response");
        changed.put("overallScore", 99);
        var value = JSON.treeToValue(changed, AiSpeakingEvaluationResponseDto.class);
        assertThatThrownBy(() -> validator.validate(value, request("precheck-insufficient"))).isInstanceOf(BusinessException.class);
    }
    @Test
    void scoredResultStillRequiresAllEightMetrics() throws Exception {
        ObjectNode changed = (ObjectNode) fixture("evaluated").get("response");
        changed.putArray("metrics");
        var value = JSON.treeToValue(changed, AiSpeakingEvaluationResponseDto.class);
        assertThatThrownBy(() -> validator.validate(value, request("evaluated"))).isInstanceOf(BusinessException.class);
    }
    @Test
    void emptyMetricsMustHaveARealPrecheckFailure() throws Exception {
        ObjectNode changed = (ObjectNode) fixture("precheck-insufficient").get("response");
        ((ObjectNode) changed.get("eligibility")).put("eligibleBeforeAi", true);
        var value = JSON.treeToValue(changed, AiSpeakingEvaluationResponseDto.class);
        assertThatThrownBy(() -> validator.validate(value, request("precheck-insufficient"))).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.01, 1.01})
    void rejectsInvalidConfidence(double confidence) {
        var r = response("evaluated");
        var changed = new AiSpeakingEvaluationResponseDto(r.requestId(), r.sessionId(), r.status(), r.overallScore(),
                confidence, r.metrics(), r.strengths(), r.improvements(), r.recommendedExpressions(),
                r.pronunciationPractice(), r.profileSignals(), r.eligibility(), r.evaluationVersion(),
                r.scoringPolicyVersion(), r.promptVersion(), r.usage());
        assertThatThrownBy(() -> validator.validate(changed, request("evaluated"))).isInstanceOf(BusinessException.class);
    }
    @Test
    void manualRetryPreservesEvidenceAndIdempotencyKey() {
        var original = request("evaluated");
        var retry = original.forManualRetry(1);
        assertThat(retry.requestId()).isNotEqualTo(original.requestId());
        assertThat(retry.idempotencyKey()).isEqualTo(original.idempotencyKey());
        assertThat(retry.sessionId()).isEqualTo(original.sessionId());
        assertThat(retry.userTurns()).isEqualTo(original.userTurns());
        assertThat(retry.assistantTurns()).isEqualTo(original.assistantTurns());
        assertThat(retry.evaluationPolicyVersion()).isEqualTo(original.evaluationPolicyVersion());
        assertThat(retry.manualRetryAttempt()).isEqualTo(1);
    }
}
