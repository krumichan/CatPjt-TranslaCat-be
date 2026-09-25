package jp.co.translacat.domain.languagelearning.speaking.evaluation.policy;

import com.fasterxml.jackson.core.type.TypeReference;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingEvaluationResponseDto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Additive metadata in existing JSON snapshots; legacy rows stay unknown, never relabelled.
 */
public record SpeakingEvidenceMetadata(List<String> evaluatedAxes, Double evaluationCoverage,
                                       String evidencePolicyVersion, String evidenceSource) {
    public static SpeakingEvidenceMetadata from(AiSpeakingEvaluationResponseDto response) {
        return new SpeakingEvidenceMetadata(response.evaluatedAxes(), response.evaluationCoverage(),
                response.evidencePolicyVersion(), response.evidenceSource());
    }

    public static SpeakingEvidenceMetadata read(String json, LanguageLearningJsonCodec codec) {
        if (json == null || json.isBlank() || json.stripLeading().startsWith("["))
            return new SpeakingEvidenceMetadata(null, null, null, null);
        Map<String, Object> data = codec.read(json, new TypeReference<Map<String, Object>>() {
        });
        Object value = data == null ? null : data.get("evidenceMetadata");
        return value == null ? new SpeakingEvidenceMetadata(null, null, null, null)
                : codec.read(codec.write(value), SpeakingEvidenceMetadata.class);
    }

    public static String eligibilitySnapshot(AiSpeakingEvaluationResponseDto response,
                                             LanguageLearningJsonCodec codec) {
        Map<String, Object> snapshot = response.eligibility() == null ? new LinkedHashMap<>()
                : codec.read(codec.write(response.eligibility()), new TypeReference<Map<String, Object>>() {
        });
        snapshot.put("evidenceMetadata", from(response));
        return codec.write(snapshot);
    }

    public static String readAloudSnapshot(AiSpeakingEvaluationResponseDto response, LanguageLearningJsonCodec codec) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("metrics", response.metrics());
        snapshot.put("evidenceMetadata", from(response));
        return codec.write(snapshot);
    }
}
