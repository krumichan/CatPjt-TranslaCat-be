package jp.co.translacat.domain.voice.websocket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.voice.config.VoicePolicyProperties;
import jp.co.translacat.domain.voice.model.VoiceStreamContext;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.domain.voice.support.VoicePolicy;
import jp.co.translacat.global.exception.BusinessException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Component
public class VoiceAiStreamClient {

    private final ObjectMapper objectMapper;
    private final VoicePolicyProperties policy;
    private final String aiServerUrl;
    private final String aiServerApiKey;
    private final HttpClient httpClient;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong circuitOpenUntil = new AtomicLong();

    public VoiceAiStreamClient(
            ObjectMapper objectMapper,
            VoicePolicyProperties policy,
            @Value("${ai-server.url}") String aiServerUrl,
            @Value("${ai-server.api-key}") String aiServerApiKey
    ) {
        this.objectMapper = objectMapper;
        this.policy = policy;
        this.aiServerUrl = aiServerUrl;
        this.aiServerApiKey = aiServerApiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(
                        Duration.ofMillis(
                                policy.getAiConnectTimeoutMs()
                        )
                )
                .build();
    }

    public CompletableFuture<Connection> connect(
            VoiceStreamContext context,
            Consumer<String> onText,
            Consumer<Throwable> onError,
            Runnable onClosed
    ) {
        if (System.currentTimeMillis() < circuitOpenUntil.get()) {
            return CompletableFuture.failedFuture(
                    new BusinessException(
                            "Voice AI circuit breaker is open.",
                            VoiceErrorCode.AI_CIRCUIT_OPEN
                    )
            );
        }

        Listener listener = new Listener(
                onText,
                onError,
                onClosed
        );
        CompletableFuture<Connection> result = httpClient
                .newWebSocketBuilder()
                .connectTimeout(
                        Duration.ofMillis(
                                policy.getAiConnectTimeoutMs()
                        )
                )
                .header("X-API-KEY", aiServerApiKey)
                .buildAsync(streamUri(), listener)
                .thenCompose(socket -> {
                    Connection connection = new Connection(socket);
                    return connection.sendText(buildStreamOpen(context))
                            .thenApply(ignored -> {
                                consecutiveFailures.set(0);
                                return connection;
                            });
                });

        result.whenComplete((ignored, error) -> {
            if (error != null) {
                registerFailure();
            }
        });

        return result;
    }

    private String buildStreamOpen(VoiceStreamContext context) {
        try {
            JsonNode storedPolicy = objectMapper.readTree(
                    context.policySnapshot()
            );
            var root = objectMapper.createObjectNode();
            root.put("type", "STREAM_OPEN");
            root.put("requestId", UUID.randomUUID().toString());
            root.put("sessionId", context.sessionId());
            root.put("channel", context.channel().name());
            root.put("mode", context.mode().name());
            root.put(
                    "sourceLanguageMode",
                    context.sourceLanguageMode().name()
            );

            if (context.manualSourceLanguage() == null) {
                root.putNull("manualSourceLanguage");
            } else {
                root.put(
                        "manualSourceLanguage",
                        context.manualSourceLanguage()
                );
            }

            if (context.lastLockedLanguage() == null) {
                root.putNull("lastLockedLanguage");
            } else {
                root.put(
                        "lastLockedLanguage",
                        context.lastLockedLanguage()
                );
            }
            root.put("targetLanguage", context.targetLanguage());

            var audio = root.putObject("audioFormat");
            audio.put("encoding", "PCM_S16LE");
            audio.put("sampleRate", VoicePolicy.PCM_S16LE_SAMPLE_RATE);
            audio.put("channels", VoicePolicy.PCM_S16LE_CHANNELS);
            audio.put(
                    "frameDurationMs",
                    policy.getFrameDurationMs()
            );

            var aiPolicy = root.putObject("policy");
            aiPolicy.put(
                    "endpointingSilenceMs",
                    storedPolicy.path("endpointingSilenceMs")
                            .asInt(policy.getEndpointingSilenceMs())
            );
            aiPolicy.put(
                    "minUtteranceDurationMs",
                    storedPolicy.path("minUtteranceDurationMs")
                            .asInt(policy.getMinUtteranceDurationMs())
            );
            aiPolicy.put(
                    "maxUtteranceDurationMs",
                    storedPolicy.path("maxUtteranceDurationMs")
                            .asInt(policy.getMaxUtteranceDurationMs())
            );
            aiPolicy.put(
                    "languageLockConfidence",
                    storedPolicy.path("languageLockConfidence")
                            .asDouble(policy.getLanguageLockConfidence())
            );
            aiPolicy.put(
                    "languageSwitchConfidence",
                    storedPolicy.path("languageSwitchConfidence")
                            .asDouble(policy.getLanguageSwitchConfidence())
            );
            aiPolicy.put(
                    "languageSwitchConsecutiveCount",
                    storedPolicy.path("languageSwitchConsecutiveCount")
                            .asInt(policy.getLanguageSwitchConsecutiveCount())
            );

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new BusinessException(
                    "Failed to create AI Voice STREAM_OPEN.",
                    VoiceErrorCode.AI_STREAM_OPEN_FAILED
            );
        }
    }

    private URI streamUri() {
        String base = aiServerUrl.endsWith("/")
                ? aiServerUrl.substring(0, aiServerUrl.length() - 1)
                : aiServerUrl;

        if (base.startsWith("https://")) {
            base = "wss://" + base.substring("https://".length());
        } else if (base.startsWith("http://")) {
            base = "ws://" + base.substring("http://".length());
        }

        return URI.create(base + "/internal/v1/voice/streams");
    }

    private void registerFailure() {
        if (consecutiveFailures.incrementAndGet()
                < policy.getAiCircuitFailureThreshold()) {
            return;
        }

        circuitOpenUntil.set(
                System.currentTimeMillis()
                        + policy.getAiCircuitOpenSeconds() * 1000L
        );
        consecutiveFailures.set(0);
    }

    public static final class Connection {

        private final WebSocket socket;
        private CompletableFuture<Void> sendTail =
                CompletableFuture.completedFuture(null);

        private Connection(WebSocket socket) {
            this.socket = socket;
        }

        public CompletableFuture<Void> sendBinary(byte[] bytes) {
            return enqueue(() -> socket.sendBinary(
                            ByteBuffer.wrap(bytes),
                            true
                    )
                    .thenApply(ignored -> null));
        }

        public CompletableFuture<Void> sendText(String text) {
            return enqueue(() -> socket.sendText(text, true)
                    .thenApply(ignored -> null));
        }

        public void abort() {
            socket.abort();
        }

        private synchronized CompletableFuture<Void> enqueue(
                Supplier<CompletableFuture<Void>> sender
        ) {
            CompletableFuture<Void> next = sendTail
                    .handle((ignored, error) -> null)
                    .thenCompose(ignored -> sender.get());
            sendTail = next;
            return next;
        }
    }

    private static final class Listener implements WebSocket.Listener {

        private final Consumer<String> onText;
        private final Consumer<Throwable> onError;
        private final Runnable onClosed;
        private final StringBuilder textBuffer = new StringBuilder();

        private Listener(
                Consumer<String> onText,
                Consumer<Throwable> onError,
                Runnable onClosed
        ) {
            this.onText = onText;
            this.onError = onError;
            this.onClosed = onClosed;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(
                WebSocket webSocket,
                CharSequence data,
                boolean last
        ) {
            synchronized (textBuffer) {
                textBuffer.append(data);
                if (last) {
                    String message = textBuffer.toString();
                    textBuffer.setLength(0);
                    onText.accept(message);
                }
            }

            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(
                WebSocket webSocket,
                int statusCode,
                String reason
        ) {
            onClosed.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(
                WebSocket webSocket,
                Throwable error
        ) {
            onError.accept(error);
        }
    }
}
