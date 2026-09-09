package jp.co.translacat.domain.languagelearning.listening.daily.model;

public record ListeningGenerationCommand(
        Long replacementForItemId,
        Integer logicalItemIndex,
        int replacementSequence,
        int manualRetryAttempt
) {
    public static ListeningGenerationCommand initial() {
        return item(1, 0);
    }

    public static ListeningGenerationCommand manualRetry(int attempt) {
        return item(1, attempt);
    }

    public static ListeningGenerationCommand item(int itemIndex, int attempt) {
        return new ListeningGenerationCommand(null, itemIndex, 0, attempt);
    }

    public boolean replacement() {
        return replacementForItemId != null;
    }
}
