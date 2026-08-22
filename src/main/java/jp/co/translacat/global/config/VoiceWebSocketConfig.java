package jp.co.translacat.global.config;

import jp.co.translacat.domain.voice.websocket.handler.VoicePublicWebSocketHandler;
import jp.co.translacat.domain.voice.websocket.interceptor.VoiceWebSocketHandshakeInterceptor;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

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
        registry.addHandler(
                        voicePublicWebSocketHandler,
                        "/api/v1/voice/sessions/*/channels/*/stream"
                )
                .addInterceptors(
                        voiceWebSocketHandshakeInterceptor
                )
                .setAllowedOriginPatterns(
                        allowedOrigins.toArray(String[]::new)
                );
    }
}
