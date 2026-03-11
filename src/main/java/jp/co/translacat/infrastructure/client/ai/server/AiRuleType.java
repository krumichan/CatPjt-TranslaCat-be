package jp.co.translacat.infrastructure.client.ai.server;

public enum AiRuleType {
    RANK("RANK"),
    NOVEL("NOVEL"),
    EPISODE("EPISODE"),
    VOICE("VOICE");

    private final String value;

    AiRuleType(final String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }
}
