package jp.co.translacat.novel.infrastructure.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class BoundedHttp {
    private BoundedHttp() {}

    public static HttpResponse<byte[]> send(HttpClient client, HttpRequest request, int maxBytes, long remainingMillis)
            throws IOException, InterruptedException {
        // 전체 body 완료까지 같은 deadline을 사용하고 실패 시 실제 전송 future를 취소한다.
        var pending = client.sendAsync(request, ignored -> new Subscriber(maxBytes));
        try {
            return pending.get(remainingMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new java.net.http.HttpTimeoutException("RESPONSE_BODY_DEADLINE");
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof IOException io) {
                throw io;
            }
            throw new IOException("HTTP_RESPONSE_FAILED");
        } finally {
            if (!pending.isDone()) {
                pending.cancel(true);
            }
        }
    }

    private static final class Subscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int maxBytes;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private Subscriber(int maxBytes) { this.maxBytes = maxBytes; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > maxBytes - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("HTTP_RESPONSE_TOO_LARGE"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
