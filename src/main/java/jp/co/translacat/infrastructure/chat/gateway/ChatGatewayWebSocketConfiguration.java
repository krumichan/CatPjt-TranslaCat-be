package jp.co.translacat.infrastructure.chat.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
@ConditionalOnProperty(prefix = "chat.gateway", name = "enabled", havingValue = "true")
public class ChatGatewayWebSocketConfiguration {
    @Bean
    ChatGatewayWebSocketHandler chatGatewayWebSocketHandler(ChatGatewayProperties properties, ChatGatewayTarget target,
                                                            ChatGatewayTokenIssuer tokens,
                                                            ChatGatewayUserAuthenticator users) {
        return new ChatGatewayWebSocketHandler(properties, target, tokens, users);
    }

    @Bean
    WebSocketConfigurer chatGatewayWebSocketConfigurer(ChatGatewayWebSocketHandler handler) {
        // Origin 문자열은 upstream에 한 번만 전달하며 최종 허용 목록은 CHAT이 검사한다.
        return registry -> register(registry, handler);
    }

    private static void register(WebSocketHandlerRegistry registry, ChatGatewayWebSocketHandler handler) {
        registry.addHandler(handler, "/ws/chat")
                .addInterceptors(new IngressHandshake())
                .setAllowedOriginPatterns("*");
    }

    static final class IngressHandshake implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler handler, Map<String, Object> attributes) {
            // 외부 서비스 인증 헤더는 거부하고, 임의 subprotocol을 협상 없이 통과시키지 않는다.
            var headers = request.getHeaders();
            if (headers.containsKey(ChatGatewayWebSocketHandler.SERVICE_HEADER)
                    || headers.getOrEmpty("Origin").size() > 1) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            List<String> offered = headers.getOrEmpty("Sec-WebSocket-Protocol").stream()
                    .flatMap(value -> Arrays.stream(value.split(","))).map(String::trim).toList();
            if (!offered.isEmpty() && offered.stream().noneMatch(List.of("v12.stomp", "v11.stomp")::contains)) {
                response.setStatusCode(HttpStatus.BAD_REQUEST);
                return false;
            }
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Exception exception) {
        }
    }
}
