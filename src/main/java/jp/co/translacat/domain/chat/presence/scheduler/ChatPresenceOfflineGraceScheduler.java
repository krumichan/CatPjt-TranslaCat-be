package jp.co.translacat.domain.chat.presence.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jp.co.translacat.domain.chat.presence.config.ChatPresenceProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the 30-second logical ONLINE grace window after the last session disconnects.
 *
 * A reconnect invalidates the pending token. The already scheduled Runnable may still
 * wake up, but it becomes a no-op when the token is no longer current.
 */
@ConditionalOnProperty(
        prefix = "translacat.chat.presence",
        name = "enabled",
        havingValue = "true"
)
@Component
public class ChatPresenceOfflineGraceScheduler {

    private final TaskScheduler taskScheduler;
    private final ChatPresenceProperties properties;
    private final ConcurrentHashMap<Long, String> pendingTokens = new ConcurrentHashMap<>();

    public ChatPresenceOfflineGraceScheduler(
            @Qualifier("chatPresenceTaskScheduler") TaskScheduler taskScheduler,
            ChatPresenceProperties properties
    ) {
        properties.validate();
        this.taskScheduler = taskScheduler;
        this.properties = properties;
    }

    public void schedule(Long userId, Runnable offlineVerification) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null.");
        }
        if (offlineVerification == null) {
            throw new IllegalArgumentException("offlineVerification must not be null.");
        }

        String token = UUID.randomUUID().toString();
        pendingTokens.put(userId, token);

        Runnable guardedTask = () -> {
            if (!pendingTokens.remove(userId, token)) {
                return;
            }
            offlineVerification.run();
        };

        if (properties.getOfflineGrace().isZero()) {
            guardedTask.run();
            return;
        }

        taskScheduler.schedule(
                guardedTask,
                Instant.now().plus(properties.getOfflineGrace())
        );
    }

    /**
     * @return true if a pending OFFLINE grace window existed and was cancelled
     */
    public boolean cancel(Long userId) {
        if (userId == null) {
            return false;
        }
        return pendingTokens.remove(userId) != null;
    }

    public boolean isPending(Long userId) {
        return userId != null && pendingTokens.containsKey(userId);
    }
}
