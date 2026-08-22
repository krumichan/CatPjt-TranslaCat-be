package jp.co.translacat.domain.voice.enums;

public enum VoiceSessionStatus {
    CREATED,
    ACTIVE,
    DEGRADED,
    COMPLETING,
    COMPLETED,
    FAILED;

    public boolean isOpen() {
        return this == CREATED
                || this == ACTIVE
                || this == DEGRADED;
    }

    public boolean isActiveLike() {
        return isOpen() || this == COMPLETING;
    }
}
