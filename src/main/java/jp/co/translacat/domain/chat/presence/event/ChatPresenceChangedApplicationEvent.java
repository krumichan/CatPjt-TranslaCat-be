package jp.co.translacat.domain.chat.presence.event;

import java.time.LocalDateTime;

/**
 * Presence transition distributed through Redis Pub/Sub and then published inside
 * each Backend instance for local WebSocket fan-out.
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
