package jp.co.translacat.infrastructure.languagelearning.resultjournal;

import jp.co.translacat.domain.languagelearning.resultjournal.model.LearningResultCaptured;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResultDeliveryRulesTest {
    static final String SOURCE = "4b8abfea-a0d1-4458-95e8-66cb5e68d9a0";
    @Test void hashesTheExactUtf8Payload() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ResultDeliveryRules.hash("abc"));
        assertNotEquals(ResultDeliveryRules.hash("{}"), ResultDeliveryRules.hash("{} "));
    }
    @Test void boundsUtf8BytesRatherThanCharacters() {
        assertEquals(64, ResultDeliveryRules.hash("가".repeat(87381)).length());
        assertThrows(IllegalArgumentException.class, () -> ResultDeliveryRules.hash("가".repeat(87382)));
    }
    @Test void requiresAnExplicitSourceIdentity() {
        assertEquals(SOURCE, ResultDeliveryRules.sourceId(SOURCE));
        for (String value : new String[] {"", "123", "local", SOURCE.toUpperCase()}) {
            assertThrows(IllegalArgumentException.class, () -> ResultDeliveryRules.sourceId(value));
        }
    }
    @Test void capsRetryBackoff() {
        assertEquals(2, ResultDeliveryRules.retryDelaySeconds(1));
        assertEquals(300, ResultDeliveryRules.retryDelaySeconds(99));
    }
    @Test void disabledByDefaultButActiveConfigurationIsValidated() {
        var config = new ResultDeliveryProperties(); config.validate(); assertFalse(config.isEnabled());
        config.setDeliveryEnabled(true);
        assertThrows(IllegalArgumentException.class, config::validate);
        config.setEnabled(true);
        assertThrows(IllegalArgumentException.class, config::validate);
        config.setSourceInstanceId(SOURCE); config.validate();
    }
    @Test void coachingCannotBeCapturedAsAResultKind() {
        assertThrows(IllegalArgumentException.class, () -> new LearningResultCaptured(1, "SESSION_COACHING", "123", "{}"));
        assertThrows(IllegalArgumentException.class, () -> new LearningResultCaptured(0, "WRITING_SCORED", "123", "{}"));
    }
    @Test void acceptsOnlyAnExactAcknowledgement() {
        var event = new ResultEnvelope(1, SOURCE, "event", 123, 1, "WRITING_SCORED", "123", "2026-09-24T03:00:00Z", "{}", ResultDeliveryRules.hash("{}"));
        assertTrue(new ResultAcknowledgement(SOURCE, "event", 123, 1, event.payloadSha256(), "RECORDED").matches(event));
        assertTrue(new ResultAcknowledgement(SOURCE, "event", 123, 1, event.payloadSha256(), "DUPLICATE").matches(event));
        assertFalse(new ResultAcknowledgement(SOURCE, "other", 123, 1, event.payloadSha256(), "RECORDED").matches(event));
        assertFalse(new ResultAcknowledgement(SOURCE, "event", 124, 1, event.payloadSha256(), "RECORDED").matches(event));
        assertFalse(new ResultAcknowledgement(SOURCE, "event", 123, 2, event.payloadSha256(), "RECORDED").matches(event));
        assertFalse(new ResultAcknowledgement(SOURCE, "event", 123, 1, event.payloadSha256(), "IGNORED").matches(event));
    }
}
