package jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.dto;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.entity.SpeakingReadAloudProblemEvaluation;

import java.time.LocalDateTime;

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
        int manualRetryLimit
) {
    public static SpeakingReadAloudProblemEvaluationResponseDto from(
            SpeakingReadAloudProblemEvaluation entity
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
                entity.getManualRetryLimit()
        );
    }
}
