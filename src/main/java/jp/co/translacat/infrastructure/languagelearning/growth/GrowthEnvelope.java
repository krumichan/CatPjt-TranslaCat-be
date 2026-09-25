package jp.co.translacat.infrastructure.languagelearning.growth;

public record GrowthEnvelope(int schemaVersion, String sourceInstanceId, String eventId, long userId, long sequence,
                             String occurredAt, String payloadJson, String payloadSha256) {
    @Override
    public String toString() {
        return "GrowthEnvelope(eventId=" + eventId + ", payload=<redacted>)";
    }
}
