package jp.co.translacat.infrastructure.languagelearning.resultjournal;

/** JSON 필드와 해시 계약은 LL ResultEnvelopeDto와 함께 버전 관리한다. */
public record ResultEnvelope(
        int schemaVersion, String sourceInstanceId, String eventId, long userId, long sequence,
        String kind, String referenceId, String occurredAt, String payloadJson, String payloadSha256
) {
    @Override public String toString() { return "ResultEnvelope(eventId=" + eventId + ", sequence=" + sequence + ", payload=<redacted>)"; }
}
