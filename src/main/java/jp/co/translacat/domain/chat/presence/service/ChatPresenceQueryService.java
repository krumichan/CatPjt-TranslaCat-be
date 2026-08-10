package jp.co.translacat.domain.chat.presence.service;

import jp.co.translacat.domain.chat.presence.config.ChatPresenceProperties;
import jp.co.translacat.domain.chat.presence.port.ChatPresenceStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatPresenceQueryService {

    private final ChatPresenceStore presenceStore;
    private final ChatPresenceProperties properties;

    /**
     * Returns the current Presence snapshot.
     *
     * <p>{@code null} means UNKNOWN/hidden. Presence is a supporting feature,
     * therefore a Redis outage must not fail the owning Chat API response.</p>
     */
    public Boolean resolveOnline(Long userId) {
        if (!properties.isEnabled() || userId == null || userId <= 0) {
            return null;
        }

        try {
            return presenceStore.isOnline(userId);
        } catch (RuntimeException exception) {
            log.warn(
                    "Presence snapshot lookup failed. userId={}. Returning UNKNOWN.",
                    userId,
                    exception
            );
            return null;
        }
    }

    /**
     * Resolves a member-list snapshot while degrading the whole Presence part to
     * UNKNOWN when Redis becomes unavailable during the read.
     */
    public Map<Long, Boolean> resolveOnlineByUserIds(
            Collection<Long> userIds
    ) {
        if (!properties.isEnabled() || userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<Long, Boolean> result = new LinkedHashMap<>();
        try {
            userIds.stream()
                    .filter(Objects::nonNull)
                    .filter(userId -> userId > 0)
                    .distinct()
                    .forEach(userId -> result.put(
                            userId,
                            presenceStore.isOnline(userId)
                    ));
            return Map.copyOf(result);
        } catch (RuntimeException exception) {
            log.warn(
                    "Presence snapshot batch lookup failed. Returning UNKNOWN for this response.",
                    exception
            );
            return Map.of();
        }
    }
}
