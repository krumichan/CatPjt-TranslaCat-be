package jp.co.translacat.domain.chat.presence.listener;

import jp.co.translacat.domain.chat.presence.service.ChatPresenceSessionLifecycleService;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatPresenceWebSocketSessionListenerTest {

    @Mock private ChatPresenceSessionLifecycleService lifecycleService;

    private ChatPresenceWebSocketSessionListener listener;

    @BeforeEach
    void setUp() {
        listener = new ChatPresenceWebSocketSessionListener(lifecycleService);
    }

    @Test
    void connected_DelegatesAuthenticatedUserAndSessionId() {
        UserPrincipal userPrincipal = mock(UserPrincipal.class);
        when(userPrincipal.getId()).thenReturn(100L);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userPrincipal, null, List.of());

        Message<byte[]> message = MessageBuilder.withPayload(new byte[0])
                .setHeader("simpSessionId", "ws-session-a")
                .build();
        SessionConnectedEvent event = mock(SessionConnectedEvent.class);
        when(event.getUser()).thenReturn(authentication);
        when(event.getMessage()).thenReturn(message);

        listener.handleConnected(event);

        verify(lifecycleService).connected(100L, "ws-session-a");
    }

    @Test
    void disconnected_DelegatesSessionIdWithoutDependingOnPrincipal() {
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getSessionId()).thenReturn("ws-session-a");

        listener.handleDisconnected(event);

        verify(lifecycleService).disconnected("ws-session-a");
    }
}
