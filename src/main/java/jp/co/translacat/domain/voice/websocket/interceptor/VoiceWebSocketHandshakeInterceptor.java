package jp.co.translacat.domain.voice.websocket.interceptor;

import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.service.VoiceWebSocketTicketService;

import lombok.RequiredArgsConstructor;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class VoiceWebSocketHandshakeInterceptor
        implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "voiceUserId";
    public static final String ATTR_SESSION_ID = "voiceSessionId";
    public static final String ATTR_CHANNEL = "voiceChannel";

    private static final Pattern PATH = Pattern.compile(
            "^/api/v1/voice/sessions/([^/]+)/channels/(SELF|REMOTE)/stream$"
    );

    private final VoiceWebSocketTicketService ticketService;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            @NotNull ServerHttpResponse response,
            @NotNull WebSocketHandler wsHandler,
            @NotNull Map<String, Object> attributes
    ) {
        Matcher matcher = PATH.matcher(request.getURI().getPath());
        if (!matcher.matches()) {
            return false;
        }

        String ticket = UriComponentsBuilder
                .fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("ticket");

        try {
            VoiceWebSocketTicketService.TicketClaims claims =
                    ticketService.parse(ticket);
            String sessionId = matcher.group(1);
            VoiceChannel channel = VoiceChannel.valueOf(
                    matcher.group(2)
            );

            if (!claims.sessionId().equals(sessionId)
                    || claims.channel() != channel) {
                return false;
            }

            attributes.put(ATTR_USER_ID, claims.userId());
            attributes.put(ATTR_SESSION_ID, sessionId);
            attributes.put(ATTR_CHANNEL, channel);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(
            @NotNull ServerHttpRequest request,
            @NotNull ServerHttpResponse response,
            @NotNull WebSocketHandler wsHandler,
            @NotNull Exception exception
    ) {
        // no-op
    }
}
