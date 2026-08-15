package jp.co.translacat.domain.languagelearning.speaking.evaluation.event;

public record SpeakingEvaluationRequestedEvent(
        Long sessionId,
        int manualRetryAttempt
) {
}
