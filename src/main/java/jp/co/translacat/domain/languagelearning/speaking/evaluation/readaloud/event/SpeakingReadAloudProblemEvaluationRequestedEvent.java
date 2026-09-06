package jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.event;

public record SpeakingReadAloudProblemEvaluationRequestedEvent(
        Long sessionId,
        int problemIndex
) {
}
