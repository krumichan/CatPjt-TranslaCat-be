package jp.co.translacat.infrastructure.chat.gateway;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatGatewayWebSocketHandlerTest {
    private static final String CONNECT = "CONNECT\naccept-version:1.2\nAuthorization:Bearer synthetic-access\n\n\0";
    private ChatGatewayProperties properties;
    private ChatGatewayUserAuthenticator users;
    private ChatGatewayTokenIssuer tokens;
    private WebSocketSession browser;
    private WebSocket upstream;
    private ChatGatewayWebSocketHandler handler;
    private CompletableFuture<WebSocket> connecting;
    private final AtomicReference<WebSocket.Listener> listener = new AtomicReference<>();
    private final AtomicReference<String> serviceToken = new AtomicReference<>();
    private final List<Runnable> timers = new ArrayList<>();
    private final AtomicBoolean open = new AtomicBoolean(true);
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> deadline;
    private HttpHeaders headers;

    @BeforeEach
    void prepare() throws Exception {
        // 준비 — 제어 가능한 연결 future와 scheduler로 race/timeout을 실제 시간 대기 없이 검사한다.
        properties = new ChatGatewayProperties();
        users = mock(ChatGatewayUserAuthenticator.class);
        tokens = mock(ChatGatewayTokenIssuer.class);
        browser = mock(WebSocketSession.class);
        upstream = mock(WebSocket.class);
        headers = new HttpHeaders();
        when(browser.getHandshakeHeaders()).thenReturn(headers);
        when(browser.getId()).thenReturn("synthetic-session");
        when(browser.isOpen()).thenAnswer(ignored -> open.get());
        doAnswer(ignored -> {
            open.set(false);
            return null;
        }).when(browser).close(any(CloseStatus.class));
        when(upstream.sendText(any(), anyBoolean())).thenReturn(CompletableFuture.completedFuture(upstream));
        when(upstream.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.completedFuture(upstream));
        var principal = mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(73L);
        when(users.authenticate("Bearer synthetic-access")).thenReturn(principal);
        when(tokens.issue(73, "chat:realtime")).thenReturn("synthetic-service-token");
        connecting = new CompletableFuture<>();
        scheduler = mock(ScheduledExecutorService.class);
        deadline = mock(ScheduledFuture.class);
        doAnswer(call -> {
            timers.add(call.getArgument(0));
            return deadline;
        })
                .when(scheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        handler = new ChatGatewayWebSocketHandler(properties, tokens, users, (session, token, callback) -> {
            serviceToken.set(token);
            listener.set(callback);
            return connecting;
        }, scheduler);
        handler.afterConnectionEstablished(browser);
    }

    @AfterEach
    void cleanup() {
        handler.destroy();
    }

    @Test
    void fragmented_connect_authenticates_once_then_forwards_original_frame_and_pending_messages() throws Exception {
        // 실행
        handler.handleMessage(browser, new TextMessage(CONNECT.substring(0, 15), false));
        verifyNoInteractions(users);
        handler.handleMessage(browser, new TextMessage(CONNECT.substring(15), true));
        handler.handleMessage(browser, new TextMessage("SUBSCRIBE\nid:sub\ndestination:/topic/chat/rooms/1\n\n\0"));
        connecting.complete(upstream);

        // 검증 — 아직 CHAT 사용자/방 인가를 대신하지 않으며 원본 STOMP를 변경하지 않는다.
        verify(users, times(1)).authenticate("Bearer synthetic-access");
        verify(tokens).issue(73, "chat:realtime");
        assertEquals("synthetic-service-token", serviceToken.get());
        verify(upstream).sendText(CONNECT + "SUBSCRIBE\nid:sub\ndestination:/topic/chat/rooms/1\n\n\0", true);
        verify(deadline).cancel(false);
        assertTrue(open.get());
    }

    @Test
    void connect_may_span_multiple_websocket_messages() throws Exception {
        // 실행
        handler.handleMessage(browser, new TextMessage(CONNECT.substring(0, 15)));
        verifyNoInteractions(users);
        handler.handleMessage(browser, new TextMessage(CONNECT.substring(15)));
        connecting.complete(upstream);

        // 검증
        verify(upstream).sendText(CONNECT, true);
        verify(users, times(1)).authenticate(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "STOMP\r\naccept-version:1.2\r\nAuthorization:Bearer synthetic-access\r\n\r\n\0",
            "\n\r\nCONNECT\nAuthorization:Bearer synthetic-access\n\n\0",
            "CONNECT\nAuthorization:Bearer synthetic-access\ncontent-length:3\n\nabc\0"
    })
    void supported_connect_preludes_preserve_wire_text(String frame) throws Exception {
        // 실행
        handler.handleMessage(browser, new TextMessage(frame));
        connecting.complete(upstream);

        // 검증
        verify(users).authenticate("Bearer synthetic-access");
        verify(upstream).sendText(frame, true);
    }

    @Test
    void declared_body_must_complete_before_authentication() throws Exception {
        // 준비
        String prefix = "CONNECT\nAuthorization:Bearer synthetic-access\ncontent-length:3\n\nab";

        // 실행
        handler.handleMessage(browser, new TextMessage(prefix));
        verifyNoInteractions(users);
        handler.handleMessage(browser, new TextMessage("c\0"));
        connecting.complete(upstream);

        // 검증
        verify(upstream).sendText(prefix + "c\0", true);
    }

    @Test
    void connect_credentials_do_not_enter_spring_decoder_trace_logging() throws Exception {
        // 준비 — 기존 Spring decoder를 쓰면 native header 전체가 TRACE에 기록되는 회귀다.
        Logger logger = (Logger) LoggerFactory.getLogger("org.springframework.messaging.simp.stomp.StompDecoder");
        Level original = logger.getLevel();
        var capture = new ListAppender<ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        logger.setLevel(Level.TRACE);
        try {
            // 실행
            handler.handleMessage(browser, new TextMessage(CONNECT));
            connecting.complete(upstream);

            // 검증
            assertTrue(
                    capture.list.stream().noneMatch(event -> event.getFormattedMessage().contains("synthetic-access")));
            verify(users).authenticate("Bearer synthetic-access");
        } finally {
            logger.setLevel(original);
            logger.detachAppender(capture);
            capture.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SEND\ndestination:/app/chat/rooms/1/messages\n\n{}\0",
            "CONNECT\naccept-version:1.2\n\n\0",
            "CONNECT\nAuthorization:Bearer synthetic-access\nAuthorization:Bearer other\n\n\0",
            "CONNECT\nAuthorization:Bearer synthetic-access\nX-Chat-Service-Authorization:forged\n\n\0",
            "CONNECT\nAuthorization:Bearer synthetic-access\ncontent-length:-1\n\n\0",
            "CONNECT\nAuthorization:Bearer synthetic-access\ncontent-length:1\n\nabc\0",
            "CONNECT\nAuthorization:Bearer synthetic-access\ncontent-length:999999999999999999\n\n\0",
            "INVALID\n\n\0"
    })
    void invalid_first_frame_never_opens_upstream(String frame) throws Exception {
        // 실행
        handler.handleMessage(browser, new TextMessage(frame));

        // 검증
        verify(browser).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(tokens);
        assertNull(listener.get());
    }

    @Test
    void invalid_current_user_is_denied_without_issuing_service_credential() throws Exception {
        // 준비
        when(users.authenticate(anyString())).thenReturn(null);

        // 실행
        handler.handleMessage(browser, new TextMessage(CONNECT));

        // 검증
        verify(browser).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(tokens);
        assertNull(listener.get());
    }

    @Test
    void incomplete_connect_deadline_closes_without_authentication() throws Exception {
        // 실행
        handler.handleMessage(browser, new TextMessage("CONNECT\nAuthorization:"));
        timers.getFirst().run();

        // 검증
        verify(browser).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(users, tokens);
    }

    @Test
    void timeout_during_current_user_lookup_rejects_late_authentication_success() throws Exception {
        // 준비 — 동기 계정 조회 자체의 취소를 주장하지 않는다. 늦은 성공이 upstream을 열지 못해야 한다.
        var principal = mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(73L);
        when(users.authenticate(anyString())).thenAnswer(ignored -> {
            timers.getFirst().run();
            return principal;
        });

        // 실행
        handler.handleMessage(browser, new TextMessage(CONNECT));

        // 검증
        verify(browser).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(tokens);
        assertNull(listener.get());
    }

    @Test
    void shutdown_denies_new_browser_connections_and_closes_pending_authentication() throws Exception {
        // 실행
        handler.destroy();
        open.set(true);
        handler.afterConnectionEstablished(browser);

        // 검증
        verify(browser, times(2)).close(CloseStatus.GOING_AWAY);
        verify(scheduler).shutdown();
        verifyNoInteractions(users, tokens);
    }

    @Test
    void late_upstream_after_browser_close_is_aborted() throws Exception {
        // 준비 — connect의 취소가 이미 늦은 경합을 재현한다.
        connecting = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }
        };
        handler.handleMessage(browser, new TextMessage(CONNECT));

        // 실행
        handler.afterConnectionClosed(browser, CloseStatus.NORMAL);
        connecting.complete(upstream);

        // 검증
        verify(upstream).abort();
        verify(upstream, never()).sendText(any(), anyBoolean());
    }

    @Test
    void upstream_connection_failure_closes_and_does_not_send_pending_data() throws Exception {
        // 실행
        handler.handleMessage(browser, new TextMessage(CONNECT));
        connecting.completeExceptionally(new IllegalStateException("synthetic-private-diagnostic"));

        // 검증
        verify(browser).close(CloseStatus.SERVER_ERROR);
        verify(upstream, never()).sendText(any(), anyBoolean());
    }

    @Test
    void oversized_connect_is_rejected_before_user_lookup() throws Exception {
        // 실행
        handler.handleMessage(browser, new TextMessage("a".repeat(ChatGatewayWebSocketHandler.MAX_BYTES + 1)));

        // 검증
        verify(browser).close(CloseStatus.TOO_BIG_TO_PROCESS);
        verifyNoInteractions(users, tokens);
    }

    @Test
    void pending_messages_are_bounded_while_upstream_is_connecting() throws Exception {
        // 준비
        handler.handleMessage(browser, new TextMessage(CONNECT));

        // 실행
        handler.handleMessage(browser, new TextMessage("a".repeat(ChatGatewayWebSocketHandler.MAX_BYTES)));

        // 검증
        verify(browser).close(CloseStatus.TOO_BIG_TO_PROCESS);
        assertTrue(connecting.isCancelled());
    }

    @Test
    void browser_fragments_preserve_unicode_and_upstream_fragments_are_reassembled() throws Exception {
        // 준비
        handler.handleMessage(browser, new TextMessage(CONNECT));
        connecting.complete(upstream);

        // 실행
        handler.handleMessage(browser, new TextMessage("SEND\n\n\uD83D", false));
        handler.handleMessage(browser, new TextMessage("\uDE00\0", true));
        listener.get().onText(upstream, "MESS", false);
        listener.get().onText(upstream, "AGE\n\nsynthetic\0", true);

        // 검증
        verify(upstream).sendText("SEND\n\n😀\0", true);
        var delivered = ArgumentCaptor.forClass(TextMessage.class);
        verify(browser).sendMessage(delivered.capture());
        assertEquals("MESSAGE\n\nsynthetic\0", delivered.getValue().getPayload());
    }

    @Test
    void slow_upstream_queue_is_bounded_even_for_empty_messages() throws Exception {
        // 준비
        when(upstream.sendText(any(), anyBoolean())).thenReturn(new CompletableFuture<>());
        handler.handleMessage(browser, new TextMessage(CONNECT));
        connecting.complete(upstream);

        // 실행
        for (int index = 0; index < 64; index++) handler.handleMessage(browser, new TextMessage(""));

        // 검증
        verify(browser).close(CloseStatus.TOO_BIG_TO_PROCESS);
        verify(upstream, times(1)).sendText(any(), anyBoolean());
    }

    @Test
    void upstream_send_failure_ends_both_sides() throws Exception {
        // 준비
        when(upstream.sendText(any(), anyBoolean())).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException()));

        // 실행
        handler.handleMessage(browser, new TextMessage(CONNECT));
        connecting.complete(upstream);

        // 검증
        verify(browser).close(CloseStatus.SERVER_ERROR);
        verify(upstream).sendClose(CloseStatus.SERVER_ERROR.getCode(), "");
    }

    @Test
    void browser_binary_and_upstream_binary_are_not_forwarded() throws Exception {
        // 실행
        handler.handleMessage(browser, new BinaryMessage(new byte[]{1}));

        // 검증
        verify(browser).close(CloseStatus.NOT_ACCEPTABLE);
        verifyNoInteractions(users);
    }

    @Test
    void upstream_binary_closes_an_established_connection() throws Exception {
        // 준비
        handler.handleMessage(browser, new TextMessage(CONNECT));
        connecting.complete(upstream);

        // 실행
        listener.get().onBinary(upstream, ByteBuffer.wrap(new byte[]{1}), true);

        // 검증
        verify(browser).close(CloseStatus.NOT_ACCEPTABLE);
    }

    @Test
    void upstream_close_discards_private_reason_and_browser_close_bounds_cleanup() throws Exception {
        // 준비
        handler.handleMessage(browser, new TextMessage(CONNECT));
        connecting.complete(upstream);

        // 실행
        listener.get().onClose(upstream, 1000, "synthetic-private-reason");
        timers.getLast().run();

        // 검증
        verify(browser).close(CloseStatus.NORMAL);
        verify(upstream).sendClose(1000, "");
        verify(upstream).abort();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void blocked_browser_send_does_not_hold_connection_close_or_shutdown(boolean shutdown) throws Exception {
        // 준비 — native 송신만 대기시켜 gateway의 자체 lock에 의한 종료 정체를 재현한다.
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(ignored -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        }).when(browser).sendMessage(any(TextMessage.class));
        handler.handleMessage(browser, new TextMessage(CONNECT));
        connecting.complete(upstream);
        var sending = CompletableFuture.runAsync(() -> listener.get().onText(upstream, "synthetic", true));
        CompletableFuture<Void> closing = null;
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));

            // 실행 — 송신을 아직 풀지 않은 상태에서 종료가 반환해야 한다.
            closing = CompletableFuture.runAsync(() -> {
                if (shutdown) handler.destroy();
                else handler.afterConnectionClosed(browser, CloseStatus.NORMAL);
            });
            closing.get(2, TimeUnit.SECONDS);

            // 검증 — upstream abort 예약도 브라우저 송신 완료와 독립적이다.
            int expectedCode = shutdown ? CloseStatus.GOING_AWAY.getCode() : CloseStatus.NORMAL.getCode();
            verify(upstream).sendClose(expectedCode, "");
            assertFalse(sending.isDone());
            timers.getLast().run();
            verify(upstream).abort();
            assertFalse(open.get());
            if (shutdown) verify(scheduler).shutdown();
        } finally {
            release.countDown();
            sending.get(3, TimeUnit.SECONDS);
            if (closing != null) closing.get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void production_connector_forwards_only_server_credential_origin_and_selected_protocol() throws Exception {
        // 준비
        handler.destroy();
        open.set(true);
        var target = mock(ChatGatewayTarget.class);
        var http = mock(HttpClient.class);
        var builder = mock(WebSocket.Builder.class);
        when(target.httpClient()).thenReturn(http);
        when(target.webSocketUri()).thenReturn(URI.create("wss://fixed.example.invalid/ws/chat"));
        when(http.newWebSocketBuilder()).thenReturn(builder);
        when(builder.connectTimeout(any(Duration.class))).thenReturn(builder);
        when(builder.header(anyString(), anyString())).thenReturn(builder);
        when(builder.subprotocols(anyString(), any(String[].class))).thenReturn(builder);
        when(builder.buildAsync(any(), any())).thenReturn(connecting);
        when(browser.getAcceptedProtocol()).thenReturn("v12.stomp");
        headers.setOrigin("https://frontend.example.invalid");
        headers.set("Host", "forged.example.invalid");
        headers.set("X-Forwarded-Host", "forged.example.invalid");
        handler = new ChatGatewayWebSocketHandler(properties, target, tokens, users);
        handler.afterConnectionEstablished(browser);

        // 실행
        handler.handleMessage(browser, new TextMessage(CONNECT));
        connecting.complete(upstream);

        // 검증 — 실제 네트워크/TLS 시험은 별도 interop이며 여기서는 builder 호출 경계만 검사한다.
        verify(builder).header(ChatGatewayWebSocketHandler.SERVICE_HEADER, "Bearer synthetic-service-token");
        verify(builder).header("Origin", "https://frontend.example.invalid");
        verify(builder, times(2)).header(anyString(), anyString());
        verify(builder).subprotocols("v12.stomp");
        verify(builder).buildAsync(eq(URI.create("wss://fixed.example.invalid/ws/chat")), any());
    }

    @Test
    void handshake_rejects_forged_service_header_and_unsupported_protocol() {
        // 준비
        var interceptor = new ChatGatewayWebSocketConfiguration.IngressHandshake();
        var request = mock(ServerHttpRequest.class);
        var response = mock(ServerHttpResponse.class);
        when(request.getHeaders()).thenReturn(headers);
        headers.set(ChatGatewayWebSocketHandler.SERVICE_HEADER, "forged");

        // 실행 / 검증
        assertFalse(interceptor.beforeHandshake(request, response, handler, new HashMap<>()));
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);

        headers.remove(ChatGatewayWebSocketHandler.SERVICE_HEADER);
        headers.set("Sec-WebSocket-Protocol", "unsupported");
        assertFalse(interceptor.beforeHandshake(request, response, handler, new HashMap<>()));
        verify(response).setStatusCode(HttpStatus.BAD_REQUEST);

        headers.set("Sec-WebSocket-Protocol", "v12.stomp, v11.stomp");
        assertTrue(interceptor.beforeHandshake(request, response, handler, new HashMap<>()));
    }

    @Test
    void duplicate_origin_is_rejected_and_forged_header_is_also_checked_after_upgrade() throws Exception {
        // 준비
        var request = mock(ServerHttpRequest.class);
        var response = mock(ServerHttpResponse.class);
        when(request.getHeaders()).thenReturn(headers);
        headers.add("Origin", "https://one.example.invalid");
        headers.add("Origin", "https://two.example.invalid");

        // 실행 / 검증
        assertFalse(new ChatGatewayWebSocketConfiguration.IngressHandshake()
                .beforeHandshake(request, response, handler, new HashMap<>()));
        headers.set(ChatGatewayWebSocketHandler.SERVICE_HEADER, "forged");
        handler.afterConnectionEstablished(browser);
        verify(browser).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(users, tokens);
    }
}
