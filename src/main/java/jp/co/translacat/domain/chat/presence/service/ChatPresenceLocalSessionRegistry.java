package jp.co.translacat.domain.chat.presence.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks only the WebSocket sessions owned by the current Backend instance.
 *
 * Redis remains the shared presence store. This registry exists so that each
 * Backend instance refreshes only the leases for sessions it actually owns.
 */
@Component
public class ChatPresenceLocalSessionRegistry {

    private final ConcurrentHashMap<String, Long> sessionsById = new ConcurrentHashMap<>();

    /**
     * @return true when this is a new local session, false for an idempotent duplicate CONNECT
     */
    public boolean register(Long userId, String sessionId) {
        validate(userId, sessionId);

        Long existingUserId = sessionsById.putIfAbsent(sessionId, userId);
        if (existingUserId == null) {
            return true;
        }
        if (!Objects.equals(existingUserId, userId)) {
            throw new IllegalStateException(
                    "WebSocket sessionId is already registered to another user. sessionId=" + sessionId
            );
        }
        return false;
    }

    public Long remove(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return sessionsById.remove(sessionId);
    }

    public List<LocalPresenceSession> snapshot() {
        return sessionsById.entrySet().stream()
                .map(entry -> new LocalPresenceSession(entry.getValue(), entry.getKey()))
                .toList();
    }

    public long countForUser(Long userId) {
        if (userId == null) {
            return 0L;
        }
        return sessionsById.values().stream()
                .filter(userId::equals)
                .count();
    }

    public int size() {
        return sessionsById.size();
    }

    private void validate(Long userId, String sessionId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null.");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank.");
        }
    }

    public record LocalPresenceSession(
            Long userId,
            String sessionId
    ) {
    }
}
