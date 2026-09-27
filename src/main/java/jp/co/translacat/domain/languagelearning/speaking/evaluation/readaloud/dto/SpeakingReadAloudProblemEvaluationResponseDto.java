package jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.dto;

import java.time.LocalDateTime;
import java.util.List;

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
}
