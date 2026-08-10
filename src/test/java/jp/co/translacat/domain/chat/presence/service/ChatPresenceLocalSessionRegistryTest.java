package jp.co.translacat.domain.chat.presence.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatPresenceLocalSessionRegistryTest {

    @Test
    void registerAndRemove_TracksMultipleSessionsPerUser() {
        ChatPresenceLocalSessionRegistry registry = new ChatPresenceLocalSessionRegistry();

        assertTrue(registry.register(100L, "session-a"));
        assertTrue(registry.register(100L, "session-b"));
        assertEquals(2L, registry.countForUser(100L));
        assertEquals(2, registry.size());

        assertEquals(100L, registry.remove("session-a"));
        assertEquals(1L, registry.countForUser(100L));
        assertEquals(1, registry.size());
    }

    @Test
    void register_SameSessionAndSameUser_IsIdempotent() {
        ChatPresenceLocalSessionRegistry registry = new ChatPresenceLocalSessionRegistry();

        assertTrue(registry.register(100L, "session-a"));
        assertFalse(registry.register(100L, "session-a"));
        assertEquals(1, registry.size());
    }

    @Test
    void register_SameSessionForDifferentUser_IsRejected() {
        ChatPresenceLocalSessionRegistry registry = new ChatPresenceLocalSessionRegistry();
        registry.register(100L, "session-a");

        assertThrows(
                IllegalStateException.class,
                () -> registry.register(200L, "session-a")
        );
    }
}
