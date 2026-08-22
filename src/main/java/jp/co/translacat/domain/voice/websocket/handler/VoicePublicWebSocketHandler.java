package jp.co.translacat.domain.voice.websocket.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.voice.config.VoicePolicyProperties;
import jp.co.translacat.domain.voice.enums.VoiceAiEventType;
import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.enums.VoiceStreamControlType;
import jp.co.translacat.domain.voice.model.VoiceStreamContext;
import jp.co.translacat.domain.voice.service.VoiceStreamCommandService;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.domain.voice.websocket.service.VoiceAiEventProcessor;
import jp.co.translacat.domain.voice.websocket.service.VoiceAiEventProcessor.ProcessedEvent;
import jp.co.translacat.domain.voice.websocket.service.VoiceAiStreamClient;
import jp.co.translacat.domain.voice.websocket.service.VoiceConnectionRegistry;
import jp.co.translacat.domain.voice.websocket.service.VoiceRelayConnection;
import jp.co.translacat.domain.voice.websocket.service.VoiceRelayConnection.BackpressureException;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static jp.co.translacat.domain.voice.websocket.interceptor.VoiceWebSocketHandshakeInterceptor.ATTR_CHANNEL;
import static jp.co.translacat.domain.voice.websocket.interceptor.VoiceWebSocketHandshakeInterceptor.ATTR_SESSION_ID;
import static jp.co.translacat.domain.voice.websocket.interceptor.VoiceWebSocketHandshakeInterceptor.ATTR_USER_ID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoicePublicWebSocketHandler
        extends AbstractWebSocketHandler {

    private static final CloseStatus TRY_AGAIN_LATER =
            new CloseStatus(1013, "Voice relay overloaded");
    private static final int PUBLIC_SEND_TIME_LIMIT_MS = 5_000;
    private static final int PUBLIC_SEND_BUFFER_LIMIT_BYTES = 256 * 1024;

    private final VoiceStreamCommandService streamCommandService;
    private final VoiceAiStreamClient aiStreamClient;
    private final VoiceAiEventProcessor eventProcessor;
    private final VoiceConnectionRegistry connectionRegistry;
    private final VoicePolicyProperties policy;
    private final ObjectMapper objectMapper;

    private final Map<String, VoiceRelayConnection> connections =
            new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get(ATTR_USER_ID);
        String voiceSessionId = (String) session.getAttributes()
                .get(ATTR_SESSION_ID);
        VoiceChannel channel = (VoiceChannel) session.getAttributes()
                .get(ATTR_CHANNEL);

        if (userId == null
                || voiceSessionId == null
                || channel == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        String connectionId = session.getId();
        VoiceStreamContext context;
        try {
            context = streamCommandService.open(
                    userId,
                    voiceSessionId,
                    channel,
                    connectionId
            );
        } catch (RuntimeException e) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        WebSocketSession safeSession =
                new ConcurrentWebSocketSessionDecorator(
                        session,
                        PUBLIC_SEND_TIME_LIMIT_MS,
                        PUBLIC_SEND_BUFFER_LIMIT_BYTES
                );
        VoiceRelayConnection relay = new VoiceRelayConnection(
                context,
                safeSession,
                policy,
                error -> onAsyncError(connectionId, error)
        );

        connections.put(connectionId, relay);
        connectionRegistry.register(
                voiceSessionId,
                connectionId,
                relay
        );

        aiStreamClient.connect(
                        context,
                        text -> onAiText(connectionId, text),
                        error -> onAsyncError(connectionId, error),
                        () -> onAiClosed(connectionId)
                )
                .whenComplete((ai, error) -> {
                    if (error != null) {
                        onAsyncError(connectionId, error);
                        return;
                    }

                    VoiceRelayConnection current =
                            connections.get(connectionId);
                    if (current == null) {
                        ai.abort();
                        return;
                    }

                    current.attachAi(ai);
                    scheduleReadyTimeout(connectionId);
                });
    }

    @Override
    protected void handleBinaryMessage(
            WebSocketSession session,
            BinaryMessage message
    ) {
        VoiceRelayConnection relay = connections.get(session.getId());
        if (relay == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        ByteBuffer payload = message.getPayload();
        byte[] bytes = new byte[payload.remaining()];
        payload.get(bytes);

        try {
            relay.acceptAudio(bytes);
        } catch (BackpressureException e) {
            streamCommandService.markBackpressured(relay.context());
            relay.sendPublic(
                    backpressureEvent(
                            relay,
                            e.getBufferedAudioMs()
                    )
            );
            relay.forceClose(TRY_AGAIN_LATER);
        } catch (BusinessException e) {
            relay.sendPublic(
                    gatewayErrorEvent(
                            relay,
                            e.getErrorCode(),
                            false
                    )
            );
            relay.forceClose(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) {
        VoiceRelayConnection relay = connections.get(session.getId());
        if (relay == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        try {
            JsonNode control = objectMapper.readTree(
                    message.getPayload()
            );
            VoiceStreamControlType type = parseControlType(
                    control.path("type").asText()
            );

            switch (type) {
                case STREAM_FLUSH -> relay.sendControl(
                        message.getPayload()
                );
                case STREAM_CLOSE -> relay.closeGracefully(
                        control.path("reason").asText("REQUESTED")
                );
            }
        } catch (BusinessException e) {
            relay.sendPublic(
                    gatewayErrorEvent(
                            relay,
                            e.getErrorCode(),
                            false
                    )
            );
            relay.forceClose(CloseStatus.POLICY_VIOLATION);
        } catch (Exception e) {
            relay.sendPublic(
                    gatewayErrorEvent(
                            relay,
                            VoiceErrorCode.INVALID_CONTROL,
                            false
                    )
            );
            relay.forceClose(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    public void handleTransportError(
            WebSocketSession session,
            Throwable exception
    ) {
        onAsyncError(session.getId(), exception);
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) {
        VoiceRelayConnection relay = connections.remove(
                session.getId()
        );
        if (relay == null) {
            return;
        }

        connectionRegistry.remove(
                relay.context().sessionId(),
                session.getId()
        );
        try {
            streamCommandService.markDisconnected(relay.context());
        } catch (RuntimeException ignored) {
            // Stale-session cleanup is the final DB safety net.
        }
        relay.forceClose(status);
    }

    private VoiceStreamControlType parseControlType(String type) {
        try {
            return VoiceStreamControlType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "Unsupported Voice control frame.",
                    VoiceErrorCode.INVALID_CONTROL
            );
        }
    }

    private void onAiText(
            String connectionId,
            String rawJson
    ) {
        VoiceRelayConnection relay = connections.get(connectionId);
        if (relay == null) {
            return;
        }

        try {
            ProcessedEvent processed = eventProcessor.process(
                    relay,
                    rawJson
            );
            if (processed.suppressed()) {
                return;
            }

            if (processed.type() == VoiceAiEventType.STREAM_READY) {
                relay.markReady();
            }

            relay.sendPublic(processed.publicJson());

            if (processed.streamClosed()) {
                relay.markClosed();
                relay.forceClose(CloseStatus.NORMAL);
                return;
            }

            if (processed.usageLimitReached()) {
                relay.closeGracefully("USAGE_LIMIT");
            }
        } catch (RuntimeException e) {
            onAsyncError(connectionId, e);
        }
    }

    private void onAiClosed(String connectionId) {
        VoiceRelayConnection relay = connections.get(connectionId);
        if (relay == null) {
            return;
        }

        if (!relay.isCloseRequested()) {
            onAsyncError(
                    connectionId,
                    new BusinessException(
                            "Voice AI WebSocket closed unexpectedly.",
                            VoiceErrorCode.AI_CONNECTION_CLOSED
                    )
            );
            return;
        }

        try {
            streamCommandService.markDisconnected(relay.context());
        } catch (RuntimeException ignored) {
            // best effort cleanup
        }
        relay.markClosed();
        relay.forceClose(CloseStatus.NORMAL);
    }

    private void onAsyncError(
            String connectionId,
            Throwable error
    ) {
        VoiceRelayConnection relay = connections.get(connectionId);
        if (relay == null) {
            return;
        }

        log.warn(
                "Voice stream failed session={} channel={} errorType={}",
                relay.context().sessionId(),
                relay.context().channel(),
                error.getClass().getSimpleName()
        );

        try {
            streamCommandService.markError(relay.context());
        } catch (RuntimeException ignored) {
            // Preserve the original stream failure.
        }

        relay.sendPublic(
                gatewayErrorEvent(
                        relay,
                        VoiceErrorCode.AI_CONNECTION_FAILED,
                        true
                )
        );
        relay.forceClose(CloseStatus.SERVER_ERROR);
    }

    private void scheduleReadyTimeout(String connectionId) {
        CompletableFuture.delayedExecutor(
                policy.getAiConnectTimeoutMs(),
                TimeUnit.MILLISECONDS
        ).execute(() -> {
            VoiceRelayConnection relay = connections.get(connectionId);
            if (relay != null && !relay.isReady()) {
                onAsyncError(
                        connectionId,
                        new BusinessException(
                                "Voice AI STREAM_READY timeout.",
                                VoiceErrorCode.AI_READY_TIMEOUT
                        )
                );
            }
        });
    }

    private String backpressureEvent(
            VoiceRelayConnection relay,
            long bufferedAudioMs
    ) {
        try {
            var root = objectMapper.createObjectNode();
            root.put("type", "BACKPRESSURE");
            root.put("eventId", UUID.randomUUID().toString());
            root.put("sessionId", relay.context().sessionId());
            root.put("channel", relay.context().channel().name());
            root.put("bufferedAudioMs", bufferedAudioMs);
            root.put("retryAfterMs", 500);

            var error = root.putObject("error");
            error.put("code", VoiceErrorCode.BE_BACKPRESSURE);
            error.put("stage", "STREAM");
            error.put("message", "Voice relay buffer is full.");
            error.put("retryable", true);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"type\":\"VOICE_PIPELINE_FAILED\","
                    + "\"error\":{\"code\":\"VOICE_BE_BACKPRESSURE\","
                    + "\"retryable\":true}}";
        }
    }

    private String gatewayErrorEvent(
            VoiceRelayConnection relay,
            String code,
            boolean retryable
    ) {
        try {
            var root = objectMapper.createObjectNode();
            root.put("type", "VOICE_PIPELINE_FAILED");
            root.put("eventId", UUID.randomUUID().toString());
            root.put("sessionId", relay.context().sessionId());
            root.put("channel", relay.context().channel().name());

            var error = root.putObject("error");
            error.put(
                    "code",
                    code == null || code.isBlank()
                            ? VoiceErrorCode.BE_INTERNAL_ERROR
                            : code
            );
            error.put("stage", "STREAM");
            error.put("message", "Voice Gateway stream error.");
            error.put("retryable", retryable);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"type\":\"VOICE_PIPELINE_FAILED\","
                    + "\"error\":{\"code\":\"VOICE_BE_INTERNAL_ERROR\","
                    + "\"retryable\":true}}";
        }
    }

    private void closeQuietly(
            WebSocketSession session,
            CloseStatus status
    ) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (Exception ignored) {
            // no-op
        }
    }
}
