package jp.co.translacat.domain.chat.presence.listener;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jp.co.translacat.domain.chat.presence.service.ChatPresenceSessionLifecycleService;
import jp.co.translacat.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Slf4j
@ConditionalOnProperty(
        prefix = "translacat.chat.presence",
        name = "enabled",
        havingValue = "true"
)
@Component
@RequiredArgsConstructor
public class ChatPresenceWebSocketSessionListener {

    private final ChatPresenceSessionLifecycleService lifecycleService;

    @EventListener
    public void handleConnected(SessionConnectedEvent event) {
        Long userId = resolveUserId(event.getUser());
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();

        if (userId == null || sessionId == null || sessionId.isBlank()) {
            log.debug(
                    "Skipping presence CONNECT because authenticated user/session is unavailable. userId={}, sessionId={}",
                    userId,
                    sessionId
            );
            return;
        }

        lifecycleService.connected(userId, sessionId);
    }

    @EventListener
    public void handleDisconnected(SessionDisconnectEvent event) {
        lifecycleService.disconnected(event.getSessionId());
    }

    private Long resolveUserId(Principal principal) {
        if (!(principal instanceof Authentication authentication)) {
            return null;
        }
        if (!(authentication.getPrincipal() instanceof UserPrincipal userPrincipal)) {
            return null;
        }
        return userPrincipal.getId();
    }
}
