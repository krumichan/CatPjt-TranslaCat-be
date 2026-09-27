package jp.co.translacat.infrastructure.chat.gateway;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class ChatGatewayWebSocketHandler extends AbstractWebSocketHandler
        implements SubProtocolCapable, DisposableBean {
    static final String SERVICE_HEADER = "X-Chat-Service-Authorization";
    static final int MAX_BYTES = 64 * 1024;
    private final ChatGatewayUserAuthenticator users;
    private final ChatGatewayTokenIssuer tokens;
    private final Connector connector;
    private final ScheduledExecutorService scheduler;
    private final int timeoutSeconds;
    private final Map<String, Bridge> bridges = new ConcurrentHashMap<>();
    private final AtomicBoolean stopping = new AtomicBoolean();

    public ChatGatewayWebSocketHandler(ChatGatewayProperties properties, ChatGatewayTarget target,
                                       ChatGatewayTokenIssuer tokens, ChatGatewayUserAuthenticator users) {
        this(properties, tokens, users, (browser, serviceToken, listener) -> {
            var builder = target.httpClient().newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header(SERVICE_HEADER, "Bearer " + serviceToken);
            String protocol = browser.getAcceptedProtocol();
            if (protocol != null && !protocol.isBlank()) builder.subprotocols(protocol);
            String origin = browser.getHandshakeHeaders().getOrigin();
            if (origin != null) builder.header("Origin", origin);
            return builder.buildAsync(target.webSocketUri(), listener);
        }, Executors.newSingleThreadScheduledExecutor(action -> {
            var thread = new Thread(action, "chat-gateway-websocket-timeouts");
            thread.setDaemon(true);
            return thread;
        }));
    }

    ChatGatewayWebSocketHandler(ChatGatewayProperties properties, ChatGatewayTokenIssuer tokens,
                                ChatGatewayUserAuthenticator users, Connector connector,
                                ScheduledExecutorService scheduler) {
        this.timeoutSeconds = properties.getTimeoutSeconds();
        this.tokens = tokens;
        this.users = users;
        this.connector = connector;
        this.scheduler = scheduler;
    }

    @Override
    public List<String> getSubProtocols() {
        return List.of("v12.stomp", "v11.stomp");
    }

    @Override
    public boolean supportsPartialMessages() {
        return true;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 브라우저가 넣은 내부 인증 헤더는 무시하는 대신 거부한다. upstream은 아직 열지 않는다.
        if (stopping.get()) {
            session.close(CloseStatus.GOING_AWAY);
            return;
        }
        if (session.getHandshakeHeaders().containsKey(SERVICE_HEADER)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        session.setTextMessageSizeLimit(MAX_BYTES);
        session.setBinaryMessageSizeLimit(MAX_BYTES);
        var bridge = new Bridge(session);
        bridges.put(session.getId(), bridge);
        try {
            bridge.deadline = scheduler.schedule(() -> close(bridge, CloseStatus.POLICY_VIOLATION),
                    timeoutSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException ignored) {
            close(bridge, CloseStatus.GOING_AWAY);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        var bridge = bridges.get(session.getId());
        if (bridge == null || bridge.closed.get()) return;

        String authorization = null;
        try {
            synchronized (bridge.inboundLock) {
                // WebSocket fragment를 한 메시지로 모으되, 최초 STOMP와 접속 대기 buffer 모두 상한을 둔다.
                bridge.fragment.append(message.getPayload());
                if (utf8Size(bridge.fragment) > MAX_BYTES) {
                    close(bridge, CloseStatus.TOO_BIG_TO_PROCESS);
                    return;
                }
                if (!message.isLast()) return;
                String complete = bridge.fragment.toString();
                bridge.fragment.setLength(0);
                if (bridge.upstream != null) {
                    forward(bridge, complete);
                    return;
                }

                bridge.pending.append(complete);
                if (utf8Size(bridge.pending) > MAX_BYTES) {
                    close(bridge, CloseStatus.TOO_BIG_TO_PROCESS);
                    return;
                }
                if (bridge.authStarted) return;
                authorization = connectAuthorization(bridge.pending.toString().getBytes(StandardCharsets.UTF_8));
                if (authorization == null) return;
                bridge.authStarted = true;
            }

            // 원래 사용자 JWT와 현재 계정을 확인한 뒤에만 서버 전용 ingress JWT를 발급한다.
            var principal = users.authenticate(authorization);
            if (principal == null) {
                close(bridge, CloseStatus.POLICY_VIOLATION);
                return;
            }
            if (bridge.closed.get()) return;
            var connecting = connector.connect(session, tokens.issue(principal.getId(), "chat:realtime"),
                    new UpstreamListener(bridge));
            bridge.connecting = connecting;
            if (bridge.closed.get()) connecting.cancel(true);
            connecting.orTimeout(timeoutSeconds, TimeUnit.SECONDS).whenComplete((upstream, failure) -> {
                if (failure != null) {
                    close(bridge, CloseStatus.SERVER_ERROR);
                    return;
                }
                synchronized (bridge.inboundLock) {
                    if (bridge.closed.get()) {
                        upstream.abort();
                        return;
                    }
                    bridge.upstream = upstream;
                    // close가 첫 closed 검사와 참조 게시 사이에서 끝난 경우도 연결을 회수한다.
                    if (bridge.closed.get()) {
                        upstream.abort();
                        return;
                    }
                    String initial = bridge.pending.toString();
                    bridge.pending.setLength(0);
                    forward(bridge, initial);
                    bridge.deadline.cancel(false);
                }
            });
        } catch (Exception ignored) {
            close(bridge, CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        close(bridges.get(session.getId()), CloseStatus.NOT_ACCEPTABLE);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        close(bridges.get(session.getId()), CloseStatus.SERVER_ERROR);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        close(bridges.get(session.getId()), safeStatus(status.getCode()));
    }

    private void forward(Bridge bridge, String text) {
        // java.net.http WebSocket의 동시 send를 직렬화하고 느린 upstream의 queue를 제한한다.
        int bytes = utf8Size(text);
        if (bridge.queuedBytes.addAndGet(bytes) > MAX_BYTES || bridge.queuedMessages.incrementAndGet() > 64) {
            close(bridge, CloseStatus.TOO_BIG_TO_PROCESS);
            return;
        }
        synchronized (bridge.sendLock) {
            bridge.sends = bridge.sends.thenCompose(ignored -> {
                if (bridge.closed.get()) return CompletableFuture.failedFuture(new IllegalStateException());
                return bridge.upstream.sendText(text, true).thenApply(sent -> (Void) null);
            }).orTimeout(timeoutSeconds, TimeUnit.SECONDS).whenComplete((ignored, failure) -> {
                bridge.queuedBytes.addAndGet(-bytes);
                bridge.queuedMessages.decrementAndGet();
                if (failure != null) close(bridge, CloseStatus.SERVER_ERROR);
            });
        }
    }

    private void close(Bridge bridge, CloseStatus status) {
        if (bridge == null || !bridge.closed.compareAndSet(false, true)) return;
        bridges.remove(bridge.browser.getId(), bridge);
        if (bridge.deadline != null) bridge.deadline.cancel(false);
        if (bridge.connecting != null && !bridge.connecting.isDone()) bridge.connecting.cancel(true);

        // 느린 브라우저 send가 끝나기를 기다리지 않고 upstream 정리부터 예약한다.
        // browserLock은 send끼리의 직렬화에만 쓰며, close와 공유하지 않는다.
        var upstream = bridge.upstream;
        if (upstream != null) {
            try {
                upstream.sendClose(safeStatus(status.getCode()).getCode(), "")
                        .exceptionally(failure -> { upstream.abort(); return upstream; });
                scheduler.schedule(upstream::abort, 3, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                upstream.abort();
            }
        }

        try {
            if (bridge.browser.isOpen()) bridge.browser.close(status);
        } catch (Exception ignored) {
            // 종료 실패로 인증이나 relay를 다시 살리지 않는다.
        }
    }

    @Override
    public void destroy() {
        stopping.set(true);
        bridges.values().forEach(bridge -> close(bridge, CloseStatus.GOING_AWAY));
        scheduler.shutdown();
    }

    private static int utf8Size(CharSequence text) {
        return text.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    private static String connectAuthorization(byte[] frame) {
        // Spring StompDecoder는 TRACE에서 native Authorization을 출력하므로 이 경계에서 사용하지 않는다.
        // 첫 CONNECT/STOMP의 완료 여부와 헤더만 검사한다. 이후 frame의 업무 해석은 CHAT에 남긴다.
        int cursor = 0;
        while (cursor < frame.length && (frame[cursor] == '\r' || frame[cursor] == '\n')) cursor++;
        int start = cursor;
        int lineEnd = indexOf(frame, (byte) '\n', cursor);
        if (lineEnd < 0) return null;
        String command = line(frame, cursor, lineEnd);
        if (!command.equals("CONNECT") && !command.equals("STOMP")) throw new IllegalArgumentException();
        cursor = lineEnd + 1;
        String authorization = null;
        int contentLength = -1;
        var names = new HashSet<String>();
        while (true) {
            lineEnd = indexOf(frame, (byte) '\n', cursor);
            if (lineEnd < 0) {
                if (frame.length - start > 8192) throw new IllegalArgumentException();
                return null;
            }
            if (lineEnd - start > 8192) throw new IllegalArgumentException();
            String header = line(frame, cursor, lineEnd);
            cursor = lineEnd + 1;
            if (header.isEmpty()) break;
            int colon = header.indexOf(':');
            if (colon <= 0 || names.size() >= 64) throw new IllegalArgumentException();
            String name = header.substring(0, colon);
            String value = header.substring(colon + 1);
            if (!names.add(name) || SERVICE_HEADER.equalsIgnoreCase(name)) throw new IllegalArgumentException();
            if (name.equals("Authorization")) authorization = value;
            if (name.equals("content-length")) {
                if (!value.matches("[0-9]+")) throw new IllegalArgumentException();
                contentLength = Integer.parseInt(value);
                if (contentLength > MAX_BYTES) throw new IllegalArgumentException();
            }
        }

        int terminator;
        if (contentLength >= 0) {
            terminator = cursor + contentLength;
            if (terminator >= MAX_BYTES) throw new IllegalArgumentException();
            if (frame.length <= terminator) return null;
            if (frame[terminator] != 0) throw new IllegalArgumentException();
        } else {
            terminator = indexOf(frame, (byte) 0, cursor);
            if (terminator < 0) return null;
        }
        if (authorization == null || authorization.isEmpty()) throw new IllegalArgumentException();
        return authorization;
    }

    private static int indexOf(byte[] value, byte target, int from) {
        for (int index = from; index < value.length; index++) {
            if (value[index] == target) return index;
        }
        return -1;
    }

    private static String line(byte[] frame, int start, int end) {
        if (end > start && frame[end - 1] == '\r') end--;
        return new String(frame, start, end - start, StandardCharsets.UTF_8);
    }

    private static CloseStatus safeStatus(int code) {
        if ((code >= 1000 && code <= 1014 && code != 1004 && code != 1005 && code != 1006)
                || code >= 3000 && code <= 4999) return new CloseStatus(code);
        return CloseStatus.GOING_AWAY;
    }

    @FunctionalInterface
    interface Connector {
        CompletableFuture<WebSocket> connect(WebSocketSession browser, String serviceToken, WebSocket.Listener listener);
    }

    private static final class Bridge {
        final WebSocketSession browser;
        final Object inboundLock = new Object();
        final Object sendLock = new Object();
        final Object browserLock = new Object();
        final AtomicBoolean closed = new AtomicBoolean();
        final AtomicInteger queuedBytes = new AtomicInteger();
        final AtomicInteger queuedMessages = new AtomicInteger();
        final StringBuilder fragment = new StringBuilder();
        final StringBuilder pending = new StringBuilder();
        volatile WebSocket upstream;
        volatile CompletableFuture<WebSocket> connecting;
        ScheduledFuture<?> deadline;
        boolean authStarted;
        CompletableFuture<Void> sends = CompletableFuture.completedFuture(null);

        Bridge(WebSocketSession browser) {
            this.browser = browser;
        }
    }

    private final class UpstreamListener implements WebSocket.Listener {
        private final Bridge bridge;
        private final StringBuilder fragment = new StringBuilder();

        private UpstreamListener(Bridge bridge) {
            this.bridge = bridge;
        }

        @Override
        public void onOpen(WebSocket socket) {
            socket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            try {
                if (bridge.closed.get()) {
                    socket.abort();
                    return CompletableFuture.completedFuture(null);
                }
                fragment.append(data);
                if (utf8Size(fragment) > MAX_BYTES) {
                    close(bridge, CloseStatus.TOO_BIG_TO_PROCESS);
                } else if (last) {
                    synchronized (bridge.browserLock) {
                        if (!bridge.closed.get()) bridge.browser.sendMessage(new TextMessage(fragment.toString()));
                    }
                    fragment.setLength(0);
                }
                socket.request(1);
            } catch (Exception ignored) {
                close(bridge, CloseStatus.SERVER_ERROR);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket socket, ByteBuffer data, boolean last) {
            close(bridge, CloseStatus.NOT_ACCEPTABLE);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            close(bridge, safeStatus(statusCode));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            close(bridge, CloseStatus.SERVER_ERROR);
        }
    }
}
