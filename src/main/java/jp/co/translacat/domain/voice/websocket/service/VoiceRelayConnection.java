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
    private final AtomicBoolean terminated = new AtomicBoolean();
    private final AtomicBoolean handlingFailure = new AtomicBoolean();
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

    public boolean isTerminated() {
        return terminated.get();
    }

    public boolean isGracefullyClosed() {
        return closedFuture.isDone() && !closedFuture.isCompletedExceptionally();
    }

    public boolean beginFailure() {
        return !terminated.get() && handlingFailure.compareAndSet(false, true);
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
        // 연결 준비 중 사용자가 종료했다면 늦게 도착한 AI 소켓도 즉시 폐기한다.
        if (terminated.get() || closeRequested.get()) {
            connection.abort();
            return;
        }

        this.aiConnection = connection;
        if (ready.get()) {
            drainPreReadyAudio();
        }
    }

    public synchronized void markReady() {
        if (terminated.get() || closeRequested.get()) {
            return;
        }

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
        if (terminated.get() || closeRequested.get()) {
            throw new BusinessException(
                    "Voice stream is closing.",
                    VoiceErrorCode.STREAM_CLOSING
            );
        }

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
        if (terminated.get() || !publicSession.isOpen()) {
            return;
        }

        try {
            publicSession.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            asyncError.accept(e);
        }
    }

    @Override
    public synchronized CompletableFuture<Void> closeGracefully(String reason) {
        if (!closeRequested.compareAndSet(false, true)) {
            return closedFuture;
        }

        VoiceAiStreamClient.Connection ai = aiConnection;
        if (ai == null) {
            // 완료 future만 끝내면 이후 attach/ready가 옛 음성을 다시 전송할 수 있다.
            markClosed();
            forceClose(CloseStatus.NORMAL);
            return closedFuture;
        }

        // Control reason은 JSON 문자열로 안전하게 보낼 수 있는 식별자로 제한한다.
        String escaped = reason != null && reason.matches("[A-Za-z0-9_-]{1,80}")
                ? reason
                : "REQUESTED";
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
                        closedFuture.completeExceptionally(error);
                        forceClose(CloseStatus.SERVER_ERROR);
                    }
                });

        return closedFuture;
    }

    public void markClosed() {
        closeRequested.set(true);
        if (!closedFuture.isDone()) {
            closedFuture.complete(null);
        }
    }

    public void markFailed(Throwable failure) {
        closeRequested.set(true);
        closedFuture.completeExceptionally(failure);
    }

    @Override
    public synchronized void forceClose(CloseStatus status) {
        if (!terminated.compareAndSet(false, true)) {
            return;
        }

        // 종료한 연결은 준비 큐를 비우고 모든 추가 송신/늦은 ready를 차단한다.
        closeRequested.set(true);
        preReadyAudio.clear();
        bufferedBytes.set(0L);

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
        // transport의 정상 close code만으로 Final drain 완료를 증명할 수 없다.
        // STREAM_CLOSED 확인 또는 AI 연결 전 취소만 markClosed로 먼저 성공을 확정한다.
        if (!closedFuture.isDone()) {
            closedFuture.completeExceptionally(new BusinessException(
                    "Voice stream closed before graceful completion.",
                    VoiceErrorCode.AI_CONNECTION_CLOSED
            ));
        }
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
