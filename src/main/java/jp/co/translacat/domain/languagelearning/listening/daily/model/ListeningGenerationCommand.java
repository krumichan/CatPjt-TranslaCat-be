package jp.co.translacat.domain.languagelearning.listening.daily.model;

public record ListeningGenerationCommand(
        Long replacementForItemId,
        Integer logicalItemIndex,
        int replacementSequence
) {
    public static ListeningGenerationCommand initial() {
        return new ListeningGenerationCommand(null, null, 0);
    }

    public boolean replacement() {
        return replacementForItemId != null;
    }
}
