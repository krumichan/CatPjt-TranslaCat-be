package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jp.co.translacat.novel.domain.*;
import jp.co.translacat.novel.infrastructure.ai.NovelAiProfile;
import jp.co.translacat.novel.infrastructure.ai.OpenAiExecutionAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class ProgressiveAiTransportTest {
    private HttpServer server;
    private final ObjectMapper json = new ObjectMapper();
    private final List<SourceEpisode.Segment> items = new SentenceSegmenter().segment(
            new EpisodeKey("syosyetu", "n123aa", "1"), "題", List.of("猫。犬。"), null, null).segments();

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void completedSentenceArrivesBeforeProviderResultAndMatchesFinal() throws Exception {
        CountDownLatch firstReceived = new CountDownLatch(1);
        CountDownLatch releaseFinal = new CountDownLatch(1);
        start(exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            var output = exchange.getResponseBody();
            output.write(field("s0", "고양이").getBytes(StandardCharsets.UTF_8));
            output.flush();
            if (!releaseFinal.await(2, TimeUnit.SECONDS)) throw new AssertionError("field callback blocked");
            output.write(field("s1", "개").getBytes(StandardCharsets.UTF_8));
            output.write(result(Map.of("s0", "고양이", "s1", "개")).getBytes(StandardCharsets.UTF_8));
            output.close();
        });
        var published = new ArrayList<String>();
        var pool = Executors.newSingleThreadExecutor();
        try {
            var running = pool.submit(() -> adapter().translateProgressive(null, "call:0:0", items, List.of(), Map.of(),
                    10_000, TranslationOptions.ResponseShape.KEYED_V4, TranslationOptions.ValidationPolicy.MINIMUM_LETTER_DIGIT_V2,
                    TranslationOptions.AnnotationPolicy.NONE, (segment, text) -> {
                        published.add(text);
                        if (published.size() == 1) firstReceived.countDown();
                    }));
            assertThat(firstReceived.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(published).containsExactly("고양이");
            assertThat(running.isDone()).isFalse();
            releaseFinal.countDown();
            assertThat(running.get(3, TimeUnit.SECONDS).items()).containsExactlyInAnyOrderEntriesOf(
                    Map.of(items.get(0).id(), "고양이", items.get(1).id(), "개"));
        } finally { releaseFinal.countDown(); pool.shutdownNow(); }
    }

    @Test void lateFinalMismatchNeverReturnsVerifiedTranslation() throws Exception {
        start(exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            var output = exchange.getResponseBody();
            output.write(field("s0", "고양이").getBytes(StandardCharsets.UTF_8));
            output.write(field("s1", "개").getBytes(StandardCharsets.UTF_8));
            output.write(result(Map.of("s0", "바뀐 고양이", "s1", "개")).getBytes(StandardCharsets.UTF_8));
            output.close();
        });
        var published = new ArrayList<String>();
        assertThatThrownBy(() -> adapter().translateProgressive(null, "call:0:0", items, List.of(), Map.of(),
                10_000, TranslationOptions.ResponseShape.KEYED_V4, TranslationOptions.ValidationPolicy.MINIMUM_LETTER_DIGIT_V2,
                TranslationOptions.AnnotationPolicy.NONE, (segment, text) -> published.add(text)))
                .hasMessage("PROGRESSIVE_FINAL_MISMATCH");
        assertThat(published).containsExactly("고양이", "개");
    }

    @Test void malformedUtf8CannotBePublishedAsReplacementCharacters() throws Exception {
        start(exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            var output = exchange.getResponseBody();
            output.write("data: {\"type\":\"field\",\"key\":\"s0\",\"value\":\"".getBytes(StandardCharsets.UTF_8));
            output.write(new byte[] {(byte) 0xC3, (byte) 0x28});
            output.write("\"}\n\n".getBytes(StandardCharsets.UTF_8));
            output.close();
        });
        var published = new ArrayList<String>();
        assertThatThrownBy(() -> adapter().translateProgressive(null, "call:0:0", items, List.of(), Map.of(),
                10_000, TranslationOptions.ResponseShape.KEYED_V4, TranslationOptions.ValidationPolicy.MINIMUM_LETTER_DIGIT_V2,
                TranslationOptions.AnnotationPolicy.NONE, (segment, text) -> published.add(text)))
                .hasMessage("AI_TRANSPORT_UNAVAILABLE");
        assertThat(published).isEmpty();
    }

    private OpenAiExecutionAdapter adapter() {
        var adapter = new OpenAiExecutionAdapter(json, "http://127.0.0.1:" + server.getAddress().getPort(),
                "synthetic-novel-key", "expected-model", "speech-model", "LUNA");
        adapter.configureProfile(new NovelAiProfile("sol-6.1-low-v1", "2026-10-04.1", "low", "default", 32768));
        adapter.configureProgressiveA(true);
        return adapter;
    }

    @Test void identifiedBlankDoesNotDiscardLaterNormalFields() throws Exception {
        start(exchange -> {
            exchange.getRequestBody().readAllBytes(); exchange.sendResponseHeaders(200, 0);
            var output = exchange.getResponseBody();
            output.write((field("s1", "개") + "data: {\"type\":\"field-error\",\"key\":\"s0\",\"code\":\"PROGRESSIVE_VALUE_BLANK\"}\n\n"
                    + result(Map.of("s0", " ", "s1", "개"))).getBytes(StandardCharsets.UTF_8)); output.close();
        });
        var failed = new ArrayList<String>(); var accepted = new ArrayList<String>();
        var translation = adapter().translateProgressive(null, "call", items, List.of(), Map.of(), 2000,
                TranslationOptions.ResponseShape.KEYED_V4, TranslationOptions.ValidationPolicy.MINIMUM_LETTER_DIGIT_V2,
                TranslationOptions.AnnotationPolicy.NONE, (segment, value) -> accepted.add(value), (segment, code) -> failed.add(segment.id()));
        assertThat(accepted).containsExactly("개"); assertThat(failed).containsExactly(items.getFirst().id());
        assertThat(translation.items()).containsOnlyKeys(items.getLast().id());
    }

    @Test void stalledBodyHonorsWholeDeadlineAfterHeaders() throws Exception {
        start(exchange -> {
            exchange.getRequestBody().readAllBytes(); exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(": connected\n\n".getBytes(StandardCharsets.UTF_8)); exchange.getResponseBody().flush();
            Thread.sleep(1500); exchange.close();
        });
        long started = System.nanoTime();
        assertThatThrownBy(() -> adapter().translateProgressive(null, "call", items, List.of(), Map.of(), 150,
                TranslationOptions.ResponseShape.KEYED_V4, TranslationOptions.ValidationPolicy.MINIMUM_LETTER_DIGIT_V2,
                TranslationOptions.AnnotationPolicy.NONE, (segment, value) -> {})).isInstanceOf(NovelProblem.class);
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(1000);
    }

    private void start(ExchangeHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/model/execute-profile-stream", exchange -> {
            try { handler.handle(exchange); } catch (Exception error) { exchange.close(); }
        });
        server.start();
    }

    private String field(String key, String value) throws Exception {
        return "data: " + json.writeValueAsString(Map.of("type", "field", "key", key, "value", value)) + "\n\n";
    }

    private String result(Map<String, String> translated) throws Exception {
        Map<String, Object> response = Map.ofEntries(
                Map.entry("output", translated), Map.entry("inputTokens", 10), Map.entry("outputTokens", 10),
                Map.entry("provider", "openai"), Map.entry("model", "expected-model"), Map.entry("providerCalls", 1),
                Map.entry("profileId", "sol-6.1-low-v1"), Map.entry("profileVersion", "2026-10-04.1"),
                Map.entry("reasoningEffort", "low"), Map.entry("serviceTier", "default"),
                Map.entry("cachedInputTokens", 0), Map.entry("reasoningTokens", 0), Map.entry("metadata", Map.of()));
        return "data: " + json.writeValueAsString(Map.of("type", "result", "result", response)) + "\n\n";
    }

    private interface ExchangeHandler { void handle(com.sun.net.httpserver.HttpExchange exchange) throws Exception; }
}
