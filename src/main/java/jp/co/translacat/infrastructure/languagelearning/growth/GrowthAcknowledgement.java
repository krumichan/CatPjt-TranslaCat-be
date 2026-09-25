package jp.co.translacat.infrastructure.languagelearning.growth;

public record GrowthAcknowledgement(String sourceInstanceId, String eventId, long userId, long sequence,
                                    String payloadSha256, String outcome) {
    public boolean matches(GrowthEnvelope event) {
        return event != null && event.sourceInstanceId().equals(sourceInstanceId) && event.eventId().equals(eventId)
                && event.userId() == userId && event.sequence() == sequence && event.payloadSha256()
                .equals(payloadSha256)
                && ("APPLIED".equals(outcome) || "DUPLICATE".equals(outcome));
    }
}
