package jp.co.translacat.domain.voice.websocket.service;

import jp.co.translacat.domain.voice.config.VoicePolicyProperties;
import jp.co.translacat.domain.voice.model.VoiceStreamContext;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.domain.voice.support.VoicePolicy;
import jp.co.translacat.global.exception.BusinessException;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class VoiceRelayConnection
        implements VoiceConnectionRegistry.GracefulConnection {

    private final VoiceStreamContext context;
    private final WebSocketSession publicSession;
    private final VoicePolicyProperties policy;
    private final Consumer<Throwable> asyncError;
    private final ArrayDeque<byte[]> preReadyAudio = new ArrayDeque<>();
    private final Map<Long, Integer> partialRevisions = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> finalizedSequences = new ConcurrentHashMap<>();
    private final AtomicLong bufferedBytes = new AtomicLong();
    private final AtomicLong pendingAiBytes = new AtomicLong();
    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicBoolean closeRequested = new AtomicBoolean();
    private final CompletableFuture<Void> closedFuture = new CompletableFuture<>();

    private volatile VoiceAiStreamClient.Connection aiConnection;

    public VoiceRelayConnection(
            VoiceStreamContext context,
            WebSocketSession publicSession,
            VoicePolicyProperties policy,
            Consumer<Throwable> asyncError
    ) {
        this.context = context;
        this.publicSession = publicSession;
        this.policy = policy;
        this.asyncError = asyncError;
    }

    public VoiceStreamContext context() {
        return context;
    }

    public boolean isReady() {
        return ready.get();
    }

    public boolean isCloseRequested() {
        return closeRequested.get();
    }

    public long globalSequence(long aiLocalSequence) {
        if (aiLocalSequence < 1) {
            throw new BusinessException(
                    "Voice utteranceSequence must be positive.",
                    VoiceErrorCode.INVALID_EVENT_SCHEMA
            );
        }
        return context.sequenceOffset() + aiLocalSequence;
    }

    public synchronized boolean acceptPartial(
            long globalSequence,
            int revision
    ) {
        if (revision < 1
                || finalizedSequences.containsKey(globalSequence)) {
            return false;
        }

        Integer previous = partialRevisions.get(globalSequence);
        if (previous != null && revision <= previous) {
            return false;
        }

        partialRevisions.put(globalSequence, revision);
        return true;
    }

    public void markFinalized(long globalSequence) {
        finalizedSequences.put(globalSequence, Boolean.TRUE);
        partialRevisions.remove(globalSequence);
    }

    public synchronized void attachAi(
            VoiceAiStreamClient.Connection connection
    ) {
        this.aiConnection = connection;
        if (ready.get()) {
            drainPreReadyAudio();
        }
    }

    public synchronized void markReady() {
        ready.set(true);
        if (aiConnection != null) {
            drainPreReadyAudio();
        }
    }

    public synchronized void acceptAudio(byte[] bytes) {
        if (bytes == null
                || bytes.length == 0
                || bytes.length > policy.getMaxAudioFrameBytes()) {
            throw new BusinessException(
                    "Invalid Voice binary audio frame size.",
                    VoiceErrorCode.INVALID_AUDIO_FRAME
            );
        }
        if (closeRequested.get()) {
            throw new BusinessException(
                    "Voice stream is closing.",
                    VoiceErrorCode.STREAM_CLOSING
            );
        }

        if (!ready.get() || aiConnection == null) {
            long next = bufferedBytes.get() + bytes.length;
            if (next > maxBufferedBytes()) {
                throw new BackpressureException(bufferedAudioMs());
            }

            preReadyAudio.add(bytes.clone());
            bufferedBytes.addAndGet(bytes.length);
            return;
        }

        sendBinaryToAi(bytes);
    }

    public void sendControl(String json) {
        VoiceAiStreamClient.Connection ai = aiConnection;
        if (ai == null) {
            throw new BusinessException(
                    "Voice AI stream is not connected.",
                    VoiceErrorCode.AI_NOT_CONNECTED
            );
        }

        ai.sendText(json).whenComplete((ignored, error) -> {
            if (error != null) {
                asyncError.accept(error);
            }
        });
    }

    public void sendPublic(String json) {
        if (!publicSession.isOpen()) {
            return;
        }

        try {
            publicSession.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            asyncError.accept(e);
        }
    }

    @Override
    public CompletableFuture<Void> closeGracefully(String reason) {
        if (!closeRequested.compareAndSet(false, true)) {
            return closedFuture;
        }

        VoiceAiStreamClient.Connection ai = aiConnection;
        if (ai == null) {
            markClosed();
            return closedFuture;
        }

        String escaped = reason == null
                ? "REQUESTED"
                : reason.replace("\"", "");
        ai.sendText(
                        "{\"type\":\"STREAM_FLUSH\",\"reason\":\""
                                + escaped
                                + "\"}"
                )
                .thenCompose(ignored -> ai.sendText(
                        "{\"type\":\"STREAM_CLOSE\",\"reason\":\""
                                + escaped
                                + "\"}"
                ))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        asyncError.accept(error);
                        markClosed();
                    }
                });

        return closedFuture;
    }

    public void markClosed() {
        if (!closedFuture.isDone()) {
            closedFuture.complete(null);
        }
    }

    public void forceClose(CloseStatus status) {
        closeRequested.set(true);

        VoiceAiStreamClient.Connection ai = aiConnection;
        if (ai != null) {
            ai.abort();
        }

        if (publicSession.isOpen()) {
            try {
                publicSession.close(status);
            } catch (IOException ignored) {
                // best effort cleanup
            }
        }
        markClosed();
    }

    public long bufferedAudioMs() {
        return bufferedBytes.get()
                / VoicePolicy.PCM_S16LE_BYTES_PER_MILLISECOND;
    }

    private void drainPreReadyAudio() {
        while (!preReadyAudio.isEmpty()) {
            byte[] bytes = preReadyAudio.removeFirst();
            bufferedBytes.addAndGet(-bytes.length);
            sendBinaryToAi(bytes);
        }
    }

    private void sendBinaryToAi(byte[] bytes) {
        long next = pendingAiBytes.addAndGet(bytes.length);
        if (next > maxBufferedBytes()) {
            pendingAiBytes.addAndGet(-bytes.length);
            throw new BackpressureException(
                    Math.max(
                            1L,
                            pendingAiBytes.get()
                                    / VoicePolicy.PCM_S16LE_BYTES_PER_MILLISECOND
                    )
            );
        }

        aiConnection.sendBinary(bytes).whenComplete((ignored, error) -> {
            pendingAiBytes.addAndGet(-bytes.length);
            if (error != null) {
                asyncError.accept(error);
            }
        });
    }

    private long maxBufferedBytes() {
        return Math.max(
                policy.getMaxAudioFrameBytes(),
                policy.getMaxRelayBufferedAudioMs()
                        * (long) VoicePolicy.PCM_S16LE_BYTES_PER_MILLISECOND
        );
    }

    public static class BackpressureException extends BusinessException {

        private final long bufferedAudioMs;

        public BackpressureException(long bufferedAudioMs) {
            super(
                    "Voice relay buffer is full.",
                    VoiceErrorCode.BACKPRESSURE
            );
            this.bufferedAudioMs = bufferedAudioMs;
        }

        public long getBufferedAudioMs() {
            return bufferedAudioMs;
        }
    }
}
