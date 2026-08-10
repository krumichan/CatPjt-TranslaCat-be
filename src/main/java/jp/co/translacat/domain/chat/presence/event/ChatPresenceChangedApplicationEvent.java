package jp.co.translacat.domain.chat.presence.event;

import java.time.LocalDateTime;

/**
 * Internal presence transition event.
 *
 * Stage 3 only publishes this application event. Room-scoped WebSocket fan-out is
 * intentionally deferred to the multi-instance/PubSub stage.
 */
public record ChatPresenceChangedApplicationEvent(
        Long userId,
        boolean online,
        LocalDateTime occurredAt
) {
    public static ChatPresenceChangedApplicationEvent online(Long userId) {
        return new ChatPresenceChangedApplicationEvent(
                userId,
                true,
                LocalDateTime.now()
        );
    }

    public static ChatPresenceChangedApplicationEvent offline(Long userId) {
        return new ChatPresenceChangedApplicationEvent(
                userId,
                false,
                LocalDateTime.now()
        );
    }
}
