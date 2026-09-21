package jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.dto;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.entity.SpeakingReadAloudProblemEvaluation;

import java.time.LocalDateTime;
import java.util.List;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.policy.SpeakingEvidenceMetadata;

public record SpeakingReadAloudProblemEvaluationResponseDto(
        int problemIndex,
        int attemptCount,
        String status,
        Integer overallScore,
        Double evaluationConfidence,
        String errorMessage,
        LocalDateTime submittedAt,
        LocalDateTime evaluatedAt,
        int manualRetryCount,
        int manualRetryLimit,
        List<String> evaluatedAxes,
        Double evaluationCoverage,
        String evidencePolicyVersion,
        String evidenceSource
) {
    public static SpeakingReadAloudProblemEvaluationResponseDto from(
            SpeakingReadAloudProblemEvaluation entity
    ) {
        return from(entity, new SpeakingEvidenceMetadata(null, null, null, null));
    }

    public static SpeakingReadAloudProblemEvaluationResponseDto from(
            SpeakingReadAloudProblemEvaluation entity, SpeakingEvidenceMetadata evidence
    ) {
        return new SpeakingReadAloudProblemEvaluationResponseDto(
                entity.getProblemIndex(),
                entity.getAttemptCount(),
                entity.getStatus(),
                entity.getOverallScore(),
                entity.getEvaluationConfidence(),
                entity.getErrorMessage(),
                entity.getSubmittedAt(),
                entity.getEvaluatedAt(),
                entity.getManualRetryCount(),
                entity.getManualRetryLimit(),
                evidence.evaluatedAxes(), evidence.evaluationCoverage(), evidence.evidencePolicyVersion(), evidence.evidenceSource()
        );
    }
}
