package jp.co.translacat.domain.chat.presence.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatPresencePropertiesTest {

    @Test
    void defaults_MatchPhase2PresencePolicy() {
        ChatPresenceProperties properties = new ChatPresenceProperties();

        assertEquals(Duration.ofSeconds(60), properties.getSessionTtl());
        assertEquals(Duration.ofSeconds(20), properties.getRefreshInterval());
        assertEquals(Duration.ofSeconds(30), properties.getOfflineGrace());
        assertDoesNotThrow(properties::validate);
    }

    @Test
    void validate_RejectsRefreshIntervalNotShorterThanSessionTtl() {
        ChatPresenceProperties properties = new ChatPresenceProperties();
        properties.setSessionTtl(Duration.ofSeconds(20));
        properties.setRefreshInterval(Duration.ofSeconds(20));

        assertThrows(IllegalStateException.class, properties::validate);
    }
}
