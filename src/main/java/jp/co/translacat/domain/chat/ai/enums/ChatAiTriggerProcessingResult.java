package jp.co.translacat.domain.chat.ai.enums;

public enum ChatAiTriggerProcessingResult {
    RESPONDED,
    SKIPPED,
    DUPLICATE,
    FAILED;

    public boolean advancesRevivalBackoff() {
        return this == RESPONDED
                || this == SKIPPED
                || this == DUPLICATE;
    }
}
