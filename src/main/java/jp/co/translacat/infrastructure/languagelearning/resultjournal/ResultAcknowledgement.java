package jp.co.translacat.infrastructure.languagelearning.resultjournal;

public record ResultAcknowledgement(String sourceInstanceId, String eventId, long userId,
                                    long sequence, String payloadSha256, String outcome) {
    public boolean matches(ResultEnvelope event) {
        return event.sourceInstanceId().equals(sourceInstanceId) && event.eventId().equals(eventId)
                && event.userId() == userId && event.sequence() == sequence
                && event.payloadSha256().equals(payloadSha256)
                && ("RECORDED".equals(outcome) || "DUPLICATE".equals(outcome));
    }
}
