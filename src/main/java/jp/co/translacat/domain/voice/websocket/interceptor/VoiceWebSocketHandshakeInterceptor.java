package jp.co.translacat.domain.voice.websocket.interceptor;

import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.service.VoiceWebSocketTicketService;

import lombok.RequiredArgsConstructor;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
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

    /** 음성 스트림 경로와 일회용 티켓을 검증하고 사용자 정보를 연결에 기록한다. */
    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            @NotNull ServerHttpResponse response,
            @NotNull WebSocketHandler wsHandler,
            @NotNull Map<String, Object> attributes
    ) {
        // 음성 스트림 경로와 WebSocket 업그레이드 요청부터 검사한다.
        Matcher matcher = PATH.matcher(request.getURI().getPath());
        if (!matcher.matches() || !"websocket".equalsIgnoreCase(request.getHeaders().getUpgrade())) {
            return false;
        }

        // 경로에 바인딩된 티켓을 읽어 사용 여부를 확인한다.
        String ticket = UriComponentsBuilder
                .fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("ticket");

        try {
            String sessionId = matcher.group(1);
            VoiceChannel channel = VoiceChannel.valueOf(
                    matcher.group(2)
            );

            VoiceWebSocketTicketService.TicketClaims claims =
                    ticketService.consume(ticket, sessionId, channel);

            // 검증한 값만 핸들러가 사용할 연결 속성에 전달한다.
            attributes.put(ATTR_USER_ID, claims.userId());
            attributes.put(ATTR_SESSION_ID, sessionId);
            attributes.put(ATTR_CHANNEL, channel);
            return true;
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
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
