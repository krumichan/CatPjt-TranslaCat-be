package jp.co.translacat.domain.chat.presence.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jp.co.translacat.domain.chat.presence.event.ChatPresenceChangedApplicationEvent;
import jp.co.translacat.domain.chat.presence.port.ChatPresenceStore;
import jp.co.translacat.domain.chat.presence.scheduler.ChatPresenceOfflineGraceScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@ConditionalOnProperty(
        prefix = "translacat.chat.presence",
        name = "enabled",
        havingValue = "true"
)
@Service
@RequiredArgsConstructor
public class ChatPresenceSessionLifecycleService {

    private final ChatPresenceStore presenceStore;
    private final ChatPresenceLocalSessionRegistry localSessionRegistry;
    private final ChatPresenceOfflineGraceScheduler offlineGraceScheduler;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Called after a STOMP/WebSocket session is fully connected and authenticated.
     * Redis failures are intentionally isolated from the WebSocket core flow.
     */
    public void connected(Long userId, String sessionId) {
        boolean newLocalSession = localSessionRegistry.register(userId, sessionId);
        boolean reconnectDuringGrace = offlineGraceScheduler.cancel(userId);

        try {
            long activeSessionCount = presenceStore.registerSession(userId, sessionId);
            if (newLocalSession && activeSessionCount == 1L && !reconnectDuringGrace) {
                publishOnline(userId);
            }
        } catch (RuntimeException e) {
            log.warn(
                    "Failed to register Redis presence session. WebSocket remains connected. userId={}, sessionId={}, cause={}",
                    userId,
                    sessionId,
                    e.getMessage()
            );
        }
    }

    /**
     * Called for STOMP/WebSocket disconnect. The sessionId→userId mapping comes from
     * the local registry so disconnect handling does not depend on Principal availability.
     */
    public void disconnected(String sessionId) {
        Long userId = localSessionRegistry.remove(sessionId);
        if (userId == null) {
            return;
        }

        try {
            long remainingSessionCount = presenceStore.removeSession(userId, sessionId);
            if (remainingSessionCount == 0L) {
                scheduleOfflineVerification(userId);
            }
        } catch (RuntimeException e) {
            // Redis failure must not break WebSocket disconnect processing.
            // A verification task is still scheduled; if Redis is available after the
            // grace period, the current shared state is checked before emitting OFFLINE.
            log.warn(
                    "Failed to remove Redis presence session. Scheduling degraded offline verification. "
                            + "userId={}, sessionId={}, cause={}",
                    userId,
                    sessionId,
                    e.getMessage()
            );
            scheduleOfflineVerification(userId);
        }
    }

    /**
     * Refreshes only sessions physically owned by this Backend instance.
     */
    public void refreshLocalSessions() {
        for (ChatPresenceLocalSessionRegistry.LocalPresenceSession session
                : localSessionRegistry.snapshot()) {
            refreshSession(session);
        }
    }

    private void refreshSession(ChatPresenceLocalSessionRegistry.LocalPresenceSession session) {
        try {
            boolean refreshed = presenceStore.refreshSession(
                    session.userId(),
                    session.sessionId()
            );

            if (refreshed) {
                return;
            }

            // Redis restart/TTL loss can remove a lease while the WebSocket is still alive.
            // Re-register from the authoritative local connection registry.
            long activeSessionCount = presenceStore.registerSession(
                    session.userId(),
                    session.sessionId()
            );

            if (activeSessionCount == 1L && !offlineGraceScheduler.isPending(session.userId())) {
                publishOnline(session.userId());
            }
        } catch (RuntimeException e) {
            log.warn(
                    "Failed to refresh Redis presence session. Chat core flow is unaffected. "
                            + "userId={}, sessionId={}, cause={}",
                    session.userId(),
                    session.sessionId(),
                    e.getMessage()
            );
        }
    }

    private void scheduleOfflineVerification(Long userId) {
        offlineGraceScheduler.schedule(
                userId,
                () -> verifyOfflineAfterGrace(userId)
        );
    }

    private void verifyOfflineAfterGrace(Long userId) {
        // A reconnect on the same instance may already exist even if Redis is degraded.
        if (localSessionRegistry.countForUser(userId) > 0L) {
            return;
        }

        try {
            if (presenceStore.getActiveSessionCount(userId) == 0L) {
                publishOffline(userId);
            }
        } catch (RuntimeException e) {
            log.warn(
                    "Failed to verify OFFLINE presence after grace. Presence remains unknown. userId={}, cause={}",
                    userId,
                    e.getMessage()
            );
        }
    }

    private void publishOnline(Long userId) {
        log.info("Chat presence transition detected. userId={}, online=true", userId);
        eventPublisher.publishEvent(ChatPresenceChangedApplicationEvent.online(userId));
    }

    private void publishOffline(Long userId) {
        log.info("Chat presence transition detected. userId={}, online=false", userId);
        eventPublisher.publishEvent(ChatPresenceChangedApplicationEvent.offline(userId));
    }
}
