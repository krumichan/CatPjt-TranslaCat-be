package jp.co.translacat.domain.voice;

import jp.co.translacat.domain.voice.config.VoicePolicyProperties;
import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.service.VoiceWebSocketTicketService;
import jp.co.translacat.domain.voice.websocket.handler.VoicePublicWebSocketHandler;
import jp.co.translacat.domain.voice.websocket.interceptor.VoiceWebSocketHandshakeInterceptor;
import jp.co.translacat.global.config.VoiceWebSocketConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.socket.config.annotation.ServletWebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoiceWebSocketTicketTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String SESSION = "11111111-1111-4111-8111-111111111111";
    private static final String OTHER_SESSION = "22222222-2222-4222-8222-222222222222";
    private static final String PATH = "/api/v1/voice/sessions/*/channels/*/stream";
    private static final String HOST = "https://voice.example";
    private static final String ALLOWED_ORIGIN = "https://app.example";

    private final VoiceWebSocketTicketService service = service(30);
    private final VoiceWebSocketHandshakeInterceptor handshake = new VoiceWebSocketHandshakeInterceptor(service);
    private final VoicePublicWebSocketHandler handler = mock(VoicePublicWebSocketHandler.class);

    @Test
    void oneOfEightConcurrentHandshakesConsumesTheTicket() throws Exception {
        String ticket = service.issue(42L, SESSION, VoiceChannel.SELF);
        var barrier = new CyclicBarrier(8);
        var results = new ArrayList<Future<Boolean>>();
        try (var workers = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) {
                results.add(workers.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return connect(ticket, SESSION, VoiceChannel.SELF);
                }));
            }
            int accepted = 0;
            for (var result : results) {
                if (result.get(5, TimeUnit.SECONDS)) accepted++;
            }
            assertThat(accepted).isEqualTo(1);
        }
    }

    @Test
    void mismatchedSessionChannelAndNonUpgradeDoNotConsumeTicket() {
        String ticket = service.issue(42L, SESSION, VoiceChannel.SELF);
        assertThat(connect(ticket, OTHER_SESSION, VoiceChannel.SELF)).isFalse();
        assertThat(connect(ticket, SESSION, VoiceChannel.REMOTE)).isFalse();
        assertThat(handshake.beforeHandshake(
                request(ticket, SESSION, VoiceChannel.SELF, false, null),
                mock(ServerHttpResponse.class), handler, new HashMap<>())).isFalse();

        var attributes = new HashMap<String, Object>();
        assertThat(handshake.beforeHandshake(
                request(ticket, SESSION, VoiceChannel.SELF, true, null),
                mock(ServerHttpResponse.class), handler, attributes)).isTrue();
        assertThat(attributes).containsEntry("voiceUserId", 42L)
                .containsEntry("voiceSessionId", SESSION)
                .containsEntry("voiceChannel", VoiceChannel.SELF);
    }

    @Test
    void reconnectAcceptsOnlyFreshTicket() {
        String first = service.issue(42L, SESSION, VoiceChannel.REMOTE);
        String next = service.issue(42L, SESSION, VoiceChannel.REMOTE);
        assertThat(next).isNotEqualTo(first);
        assertThat(connect(first, SESSION, VoiceChannel.REMOTE)).isTrue();

        var response = mock(ServerHttpResponse.class);
        assertThat(handshake.beforeHandshake(
                request(first, SESSION, VoiceChannel.REMOTE, true, null),
                response, handler, new HashMap<>())).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        assertThat(connect(next, SESSION, VoiceChannel.REMOTE)).isTrue();
    }

    @Test
    void expiresAndRejectsTamperedTicketsAndTicketsFromAnotherInstance() {
        var expiredService = service(-1);
        var expiredHandshake = new VoiceWebSocketHandshakeInterceptor(expiredService);
        String expired = expiredService.issue(42L, SESSION, VoiceChannel.SELF);
        assertThat(expiredHandshake.beforeHandshake(
                request(expired, SESSION, VoiceChannel.SELF, true, null),
                mock(ServerHttpResponse.class), handler, new HashMap<>())).isFalse();
        assertThat(connect(service(30).issue(42L, SESSION, VoiceChannel.SELF), SESSION, VoiceChannel.SELF)).isFalse();

        String valid = service.issue(42L, SESSION, VoiceChannel.SELF);
        int index = valid.lastIndexOf('.') + 1;
        char changed = valid.charAt(index) == 'A' ? 'B' : 'A';
        String tampered = valid.substring(0, index) + changed + valid.substring(index + 1);
        assertThat(connect(tampered, SESSION, VoiceChannel.SELF)).isFalse();
        assertThat(connect(valid, SESSION, VoiceChannel.SELF)).isTrue();
    }

    @Test
    void registeredOriginCheckRunsBeforeTicketConsumption() throws Exception {
        var registry = new ServletWebSocketHandlerRegistry();
        var config = new VoiceWebSocketConfig(handler, handshake);
        ReflectionTestUtils.setField(config, "allowedOrigins", List.of(ALLOWED_ORIGIN));
        config.registerWebSocketHandlers(registry);
        var mapping = (SimpleUrlHandlerMapping) registry.getHandlerMapping();
        var httpHandler = (WebSocketHttpRequestHandler) mapping.getUrlMap().get(PATH);
        var interceptors = httpHandler.getHandshakeInterceptors();

        String ticket = service.issue(42L, SESSION, VoiceChannel.SELF);
        assertThat(interceptors.get(1)).isSameAs(handshake);
        var denied = mock(ServerHttpResponse.class);
        assertThat(interceptors.getFirst().beforeHandshake(
                request(ticket, SESSION, VoiceChannel.SELF, true, "https://untrusted.example"),
                denied, handler, new HashMap<>())).isFalse();
        verify(denied).setStatusCode(HttpStatus.FORBIDDEN);
        var accepted = request(ticket, SESSION, VoiceChannel.SELF, true, ALLOWED_ORIGIN);
        for (var interceptor : interceptors) {
            assertThat(interceptor.beforeHandshake(accepted,
                    mock(ServerHttpResponse.class), handler, new HashMap<>())).isTrue();
        }
    }

    private boolean connect(String ticket, String session, VoiceChannel channel) {
        return handshake.beforeHandshake(request(ticket, session, channel, true, null),
                mock(ServerHttpResponse.class), handler, new HashMap<>());
    }

    private static ServerHttpRequest request(String ticket, String session, VoiceChannel channel,
                                             boolean upgrade, String origin) {
        var request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create(
                HOST + "/api/v1/voice/sessions/" + session + "/channels/" + channel + "/stream?ticket=" + ticket));
        var headers = new HttpHeaders();
        if (upgrade) headers.setUpgrade("websocket");
        if (origin != null) headers.setOrigin(origin);
        when(request.getHeaders()).thenReturn(headers);
        return request;
    }

    private static VoiceWebSocketTicketService service(int ttlSeconds) {
        var policy = mock(VoicePolicyProperties.class);
        when(policy.getWebsocketTicketTtlSeconds()).thenReturn(ttlSeconds);
        return new VoiceWebSocketTicketService(KEY, policy);
    }
}
