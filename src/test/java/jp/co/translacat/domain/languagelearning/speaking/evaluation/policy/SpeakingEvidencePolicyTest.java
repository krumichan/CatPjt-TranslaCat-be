package jp.co.translacat.domain.languagelearning.speaking.evaluation.policy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.validator.SpeakingEvaluationResponseValidator;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static jp.co.translacat.domain.languagelearning.speaking.evaluation.release.SpeakingReleaseFixtures.*;
import static org.assertj.core.api.Assertions.*;

class SpeakingEvidencePolicyTest {
    private final LanguageLearningJsonCodec codec = new LanguageLearningJsonCodec(JSON);
    private final SpeakingEvaluationResponseValidator validator = new SpeakingEvaluationResponseValidator();

    @ParameterizedTest
    @ValueSource(
            strings = {"evaluated", "precheck-insufficient", "model-insufficient", "read-aloud-script-observation"})
    void actualNewAiServiceV2FixturesAreAcceptedWithoutRewritingLegacy(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/speaking-release-v2/" + name + ".json")) {
            var fixture = JSON.readTree(stream);
            var request = JSON.treeToValue(fixture.get("request"), AiSpeakingEvaluationRequestDto.class);
            var response = JSON.treeToValue(fixture.get("response"), AiSpeakingEvaluationResponseDto.class);
            assertThatCode(() -> validator.validate(response, request)).doesNotThrowAnyException();
            assertThat(response.evidencePolicyVersion()).isEqualTo("speaking-transcript-evidence-v2");
            if (name.equals("read-aloud-script-observation")) {
                assertThat(response.evaluatedAxes()).containsExactly("MEANING");
                assertThat(response.evaluationCoverage()).isEqualTo(.1);
                assertThat(response.profileSignals()).isEmpty();
            }
        }
    }

    private ObjectNode textOnly() {
        ObjectNode value = (ObjectNode) fixture("evaluated").get("response");
        value.put("evidencePolicyVersion", "speaking-transcript-evidence-v2");
        value.put("evidenceSource", "TRANSCRIPT_OBSERVATION");
        value.put("evaluationCoverage", .65);
        var axes = value.putArray("evaluatedAxes");
        for (var item : value.get("metrics")) {
            var metric = (ObjectNode) item;
            String type = metric.get("type").asText();
            if (List.of("PRONUNCIATION", "FLUENCY").contains(type)) {
                metric.put("state", "NOT_EVALUABLE");
                metric.putNull("score");
                metric.putArray("evidence");
                metric.put("notEvaluableReason", "NO_ACOUSTIC_SCORER");
            } else axes.add(type);
        }
        value.putArray("profileSignals");
        value.putArray("pronunciationPractice");
        return value;
    }

    @Test
    void sixTextAxesRemainEvaluableAndMetadataSurvivesExistingJsonSnapshots() throws Exception {
        var value = JSON.treeToValue(textOnly(), AiSpeakingEvaluationResponseDto.class);
        assertThatCode(() -> validator.validate(value, request("evaluated"))).doesNotThrowAnyException();
        String eligibility = SpeakingEvidenceMetadata.eligibilitySnapshot(value, codec);
        String readAloud = SpeakingEvidenceMetadata.readAloudSnapshot(value, codec);
        for (String snapshot : List.of(eligibility, readAloud)) {
            var restored = SpeakingEvidenceMetadata.read(snapshot, codec);
            assertThat(restored.evaluationCoverage()).isEqualTo(.65);
            assertThat(restored.evaluatedAxes()).hasSize(6).doesNotContain("PRONUNCIATION", "FLUENCY");
            assertThat(restored.evidencePolicyVersion()).isEqualTo("speaking-transcript-evidence-v2");
        }
        assertThat(JSON.readTree(eligibility).get("eligibleBeforeAi").asBoolean()).isTrue();
        assertThat(JSON.readTree(readAloud).get("metrics")).hasSize(8);
    }

    @Test
    void legacySnapshotsRemainUnknownWithoutRelabellingHistoricalScores() {
        for (String value : List.of("[]", "{}", "null")) {
            var restored = SpeakingEvidenceMetadata.read(value, codec);
            assertThat(restored.evidencePolicyVersion()).isNull();
            assertThat(restored.evaluationCoverage()).isNull();
        }
        assertThatCode(
                () -> validator.validate(response("evaluated"), request("evaluated"))).doesNotThrowAnyException();
    }

    @Test
    void newAiJobResponseCannotOmitEvidencePolicyAndFallBackToLegacyRules() throws Exception {
        var newRequest = (ObjectNode) fixture("evaluated").get("request");
        newRequest.put("evaluationPolicyVersion", "speaking-evaluation-policy-v2");
        var request = JSON.treeToValue(newRequest, AiSpeakingEvaluationRequestDto.class);
        assertThatThrownBy(() -> validator.validate(response("evaluated"), request))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRONUNCIATION", "FLUENCY"})
    void v2RejectsUnsupportedAcousticScore(String type) throws Exception {
        var node = textOnly();
        for (var item : node.get("metrics"))
            if (item.get("type").asText().equals(type)) {
                ((ObjectNode) item).put("state", "EVALUATED").put("score", 90);
            }
        var value = JSON.treeToValue(node, AiSpeakingEvaluationResponseDto.class);
        assertThatThrownBy(() -> validator.validate(value, request("evaluated"))).isInstanceOf(BusinessException.class);
    }

    @Test
    void mismatchedCoverageIsRejected() throws Exception {
        var node = textOnly().put("evaluationCoverage", 1.0);
        var value = JSON.treeToValue(node, AiSpeakingEvaluationResponseDto.class);
        assertThatThrownBy(() -> validator.validate(value, request("evaluated"))).isInstanceOf(BusinessException.class);
    }

    @Test
    void lowConfidenceTranscriptCannotGroundMetricEvenWhenOverallEligibilityPasses() throws Exception {
        ObjectNode node = (ObjectNode) fixture("evaluated").get("request");
        for (var turn : node.get("userTurns")) ((ObjectNode) turn).put("sttConfidence", .4);
        var low = JSON.treeToValue(node, AiSpeakingEvaluationRequestDto.class);
        var value = JSON.treeToValue(textOnly(), AiSpeakingEvaluationResponseDto.class);
        assertThatThrownBy(() -> validator.validate(value, low)).isInstanceOf(BusinessException.class);
    }
}
