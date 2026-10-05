package jp.co.translacat.infrastructure.novel.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

final class NovelGatewayBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private static final int LIMIT = 8 * 1024 * 1024;
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private final ByteArrayOutputStream body = new ByteArrayOutputStream();
    private Flow.Subscription subscription;

    public CompletionStage<byte[]> getBody() { return result; }
    public void onSubscribe(Flow.Subscription value) {
        subscription = value;
        value.request(1);
    }
    public void onNext(List<ByteBuffer> buffers) {
        // 오디오 base64도 상한 안에서만 수신하며 초과 시 네트워크 구독을 취소한다.
        for (ByteBuffer buffer : buffers) {
            if (buffer.remaining() > LIMIT - body.size()) {
                subscription.cancel();
                result.completeExceptionally(new IOException("Novel response limit exceeded."));
                return;
            }
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            body.writeBytes(bytes);
        }
        subscription.request(1);
    }
    public void onError(Throwable error) { result.completeExceptionally(error); }
    public void onComplete() { result.complete(body.toByteArray()); }
}
