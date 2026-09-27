package jp.co.translacat.infrastructure.chat.gateway;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

final class ChatGatewayBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private final ByteArrayOutputStream body = new ByteArrayOutputStream();
    private Flow.Subscription subscription;

    @Override
    public CompletionStage<byte[]> getBody() {
        return result;
    }

    @Override
    public void onSubscribe(Flow.Subscription value) {
        subscription = value;
        subscription.request(1);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
        // 응답 전체를 받은 뒤에만 상태/본문을 공개한다. 크기 초과 응답을 부분 성공으로 보내지 않는다.
        for (ByteBuffer buffer : buffers) {
            if (buffer.remaining() > MAX_RESPONSE_BYTES - body.size()) {
                subscription.cancel();
                result.completeExceptionally(new IOException("Chat gateway response exceeded its limit."));
                return;
            }
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            body.writeBytes(bytes);
        }
        subscription.request(1);
    }

    @Override
    public void onError(Throwable error) {
        result.completeExceptionally(error);
    }

    @Override
    public void onComplete() {
        result.complete(body.toByteArray());
    }
}
