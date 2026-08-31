package jp.co.translacat.domain.languagelearning.level.pool.event;

public record LevelTestQuestionPrefetchRequestedEvent(
        Long sessionId,
        int questionNumber,
        int complexityBand
) {
}
