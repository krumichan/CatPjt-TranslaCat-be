package jp.co.translacat.domain.chat.presence.port;

import jp.co.translacat.domain.chat.presence.event.ChatPresenceChangedApplicationEvent;

/**
 * Publishes a globally claimed presence transition.
 *
 * <p>Stage 4 uses Redis Pub/Sub so every Backend instance can fan the same transition
 * out to its locally connected WebSocket clients.</p>
 */
public interface ChatPresenceTransitionPublisher {

    void publish(ChatPresenceChangedApplicationEvent event);
}
