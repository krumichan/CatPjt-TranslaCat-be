package jp.co.translacat.global.config;

import jp.co.translacat.domain.voice.websocket.handler.VoicePublicWebSocketHandler;
import jp.co.translacat.domain.voice.websocket.interceptor.VoiceWebSocketHandshakeInterceptor;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.OriginHandshakeInterceptor;

import java.util.List;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class VoiceWebSocketConfig implements WebSocketConfigurer {

    private final VoicePublicWebSocketHandler voicePublicWebSocketHandler;
    private final VoiceWebSocketHandshakeInterceptor voiceWebSocketHandshakeInterceptor;

    @Value("${cors.allowed-origin}")
    private List<String> allowedOrigins;

    @Override
    public void registerWebSocketHandlers(
            WebSocketHandlerRegistry registry
    ) {
        // 기존 Origin 정책을 티켓 소비보다 먼저 적용한다.
        OriginHandshakeInterceptor origin = new OriginHandshakeInterceptor();
        origin.setAllowedOriginPatterns(allowedOrigins);
        registry.addHandler(
                        voicePublicWebSocketHandler,
                        "/api/v1/voice/sessions/*/channels/*/stream"
                )
                .addInterceptors(
                        origin,
                        voiceWebSocketHandshakeInterceptor
                )
                .setAllowedOriginPatterns(
                        allowedOrigins.toArray(String[]::new)
                );
    }
}
