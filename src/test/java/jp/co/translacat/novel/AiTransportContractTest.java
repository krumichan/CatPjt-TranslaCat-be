package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.NovelProblem;
import jp.co.translacat.novel.domain.SentenceSegmenter;
import jp.co.translacat.novel.domain.SourceEpisode;
import jp.co.translacat.novel.infrastructure.ai.OpenAiExecutionAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class AiTransportContractTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private OpenAiExecutionAdapter adapter;
    private List<SourceEpisode.Segment> segments;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int status = 200;
    private volatile String response;
    private volatile String body;
    private volatile long bodyDelayMillis;
    private volatile String traceHeader;
    private volatile String requestedPath;

    @BeforeEach
    void setup() throws Exception {
        // 준비: 유료 provider가 아닌 loopback HTTP 계약 fixture.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/model/execute", exchange -> {
            requests.incrementAndGet();
            traceHeader = exchange.getRequestHeaders().getFirst("X-Novel-Trace-Id");
            requestedPath = exchange.getRequestURI().getPath();
            body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            if (bodyDelayMillis > 0) {
                try { Thread.sleep(bodyDelayMillis); } catch (InterruptedException ignored) { }
            }
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        adapter = new OpenAiExecutionAdapter(json, "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-only-key", "expected-model", "expected-speech", "LUNA");
        segments = new SentenceSegmenter().segment(new EpisodeKey("syosyetu", "n123aa", "1"), "題",
                List.of("猫。犬。"), null, null).segments();
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void optInRubyReachesHttpAndPlaceholderCannotReturnAsValidatedTranslation() throws Exception {
        var source = new SentenceSegmenter().segment(new EpisodeKey("syosyetu", "n123aa", "1"), "題",
                List.of("遥は来た。"), null, null, List.of(List.of(new SourceEpisode.RubyToken(0, 1, "遥", "はるか"))));
        response = json.writeValueAsString(Map.of("provider", "openai", "model", "expected-model", "providerCalls", 1,
                "inputTokens", 1, "outputTokens", 1, "output", Map.of("s0", "」")));
        assertThatThrownBy(() -> adapter.translate("c1111111-1111-4111-8111-111111111111", "guard:0:0",
                source.segments(), List.of(), Map.of(), 3000, jp.co.translacat.novel.domain.TranslationOptions.ResponseShape.KEYED_V4,
                jp.co.translacat.novel.domain.TranslationOptions.ValidationPolicy.MINIMUM_LETTER_DIGIT_V2,
                jp.co.translacat.novel.domain.TranslationOptions.AnnotationPolicy.SOURCE_RUBY_V1))
                .hasMessage("TRANSLATION_MINIMUM_CONTENT_INVALID");
        var request = json.readTree(body);
        var data = json.readTree(request.path("messages").get(0).path("content").asText());
        assertThat(data.at("/items/0/rubyTokens/0/reading").asText()).isEqualTo("はるか");
        assertThat(request.path("responseSchema").path("required")).isEqualTo(json.valueToTree(List.of("s0")));
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void strictGenericRequestCarriesOneCallDeadlineAndSourceAsData() throws Exception {
        response = json.writeValueAsString(Map.of("provider", "openai", "model", "expected-model", "providerCalls", 1,
                "inputTokens", 10, "outputTokens", 20, "output", Map.of("items", List.of(
                        Map.of("id", "s1", "text", "개"), Map.of("id", "s0", "text", "고양이")))));

        // 실행
        var translated = adapter.translate("trace-1", segments, List.of("隣の文。"), 3210);

        // 검증
        var sent = json.readTree(body);
        assertThat(sent.path("maxProviderCalls").asInt()).isEqualTo(1);
        assertThat(sent.path("remainingMilliseconds").asInt()).isEqualTo(3210);
        assertThat(sent.path("strict").asBoolean()).isTrue();
        assertThat(sent.path("reasoningEffort").asText()).isEqualTo("none");
        assertThat(sent.has("tools")).isFalse();
        var input = json.readTree(sent.path("messages").get(0).path("content").asText());
        assertThat(input.path("items").get(0).path("id").asText()).isEqualTo("s0");
        assertThat(input.path("items").get(1).path("id").asText()).isEqualTo("s1");
        assertThat(sent.path("responseSchema").path("properties").path("items").path("items")
                .path("properties").path("id").path("enum")).isEqualTo(json.valueToTree(List.of("s0", "s1")));
        assertThat(translated.items()).containsExactlyInAnyOrderEntriesOf(Map.of(
                segments.getFirst().id(), "고양이", segments.getLast().id(), "개"));
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void retryAfterSecondsSurvivesGenericErrorContractWithoutLeakingMessage() {
        // 준비
        status = 503;
        response = "{\"detail\":{\"code\":\"PROVIDER_UNAVAILABLE\",\"retryable\":true,\"retryAfterSeconds\":2,\"message\":\"sensitive\"}}";

        // 실행 및 검증
        assertThatThrownBy(() -> adapter.translate("retry", segments, List.of(), 1000))
                .isInstanceOfSatisfying(NovelProblem.class, problem -> {
                    assertThat(problem.retryAfterMillis()).isEqualTo(2000);
                    assertThat(problem.retryable()).isTrue();
                    assertThat(problem.getMessage()).isEqualTo("PROVIDER_UNAVAILABLE");
                });
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void providerMismatchAndRefusalCannotBecomeSuccessfulOrRetryable() throws Exception {
        // 준비 및 실행: 공급자 identity 위조를 거부한다.
        response = json.writeValueAsString(Map.of("provider", "gemini", "model", "expected-model", "providerCalls", 1,
                "inputTokens", 1, "outputTokens", 1, "output", Map.of("items", List.of())));
        assertThatThrownBy(() -> adapter.translate("mismatch", segments, List.of(), 1000)).hasMessage("AI_PROVIDER_IDENTITY_MISMATCH");

        // 실행 및 검증: retryable=true인 잘못된 upstream refusal도 우회하지 않는다.
        status = 503;
        response = "{\"detail\":{\"code\":\"PROVIDER_REFUSAL\",\"retryable\":true}}";
        assertThatThrownBy(() -> adapter.translate("refused", segments, List.of(), 1000))
                .isInstanceOfSatisfying(NovelProblem.class, problem -> assertThat(problem.retryable()).isFalse());
    }

    @Test
    void stalledResponseBodyCannotOutliveTheWholeRequestDeadline() {
        // 준비: headers는 즉시 오지만 본문은 deadline 이후에 도착한다.
        response = "{}";
        bodyDelayMillis = 1200;
        long started = System.nanoTime();

        // 실행 및 검증
        assertThatThrownBy(() -> adapter.translate("body-timeout", segments, List.of(), 150))
                .hasMessage("PROVIDER_TIMEOUT");
        assertThat(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(800);
    }

    @Test
    void workGlossaryIsStructuredDataAndPlainRemoteHttpIsRejected() throws Exception {
        // 준비
        response = json.writeValueAsString(Map.of("provider", "openai", "model", "expected-model", "providerCalls", 1,
                "inputTokens", 10, "outputTokens", 20, "output", Map.of("items", List.of(
                        Map.of("id", "s0", "text", "고양이"), Map.of("id", "s1", "text", "개")))));

        // 실행
        adapter.translate("with-glossary", segments, List.of(), Map.of("猫", "고양이"), 1000);

        // 검증
        var message = json.readTree(body).path("messages").get(0).path("content").asText();
        assertThat(json.readTree(message).path("glossary").path("猫").asText()).isEqualTo("고양이");
        assertThatThrownBy(() -> new OpenAiExecutionAdapter(json, "http://ai.remote.invalid", "key", "model", "speech", "LUNA"))
                .hasMessage("NOVEL_AI_URL_INVALID");
    }

    @Test
    void isolatedProfileCannotSilentlyChangeIdentityAndRootTraceIsNotTheCallId() throws Exception {
        adapter.configureProfile(new jp.co.translacat.novel.infrastructure.ai.NovelAiProfile("verified-profile", "v1", "low", "default", 32768));
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("provider", "openai"); result.put("model", "expected-model"); result.put("providerCalls", 1);
        result.put("profileId", "verified-profile"); result.put("profileVersion", "v1"); result.put("reasoningEffort", "low");
        result.put("serviceTier", "default"); result.put("inputTokens", 100); result.put("outputTokens", 50);
        result.put("cachedInputTokens", 20); result.put("reasoningTokens", 10);
        result.put("metadata", Map.of("queueMs", 3.0, "providerMs", 20.0, "providerRequestId", "req_safe", "secret", "must-not-copy"));
        result.put("output", Map.of("items", List.of(Map.of("id", "s0", "text", "고양이"), Map.of("id", "s1", "text", "개"))));
        response = json.writeValueAsString(result);
        String trace = "aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa";
        var translated = adapter.translate(trace, "job:0:1", segments, List.of(), Map.of(), 3000);
        var request = json.readTree(body);
        assertThat(requestedPath).isEqualTo("/internal/v1/model/execute-profile");
        assertThat(traceHeader).isEqualTo(trace);
        assertThat(request.path("traceId").asText()).isEqualTo("job:0:1");
        assertThat(request.has("model") || request.has("tier") || request.has("reasoningEffort")).isFalse();
        assertThat(request.path("maxOutputTokens").asInt()).isEqualTo(32768);
        assertThat(translated.timings()).containsEntry("aiQueueMs", 3.0).containsEntry("providerMs", 20.0)
                .containsEntry("providerTtftMs", null).doesNotContainKey("secret");
        result.put("profileVersion", "changed-v2"); response = json.writeValueAsString(result);
        assertThatThrownBy(() -> adapter.translate(trace, "job:1:1", segments, List.of(), Map.of(), 3000))
                .hasMessage("AI_PROFILE_IDENTITY_MISMATCH");
        assertThat(requests.get()).isEqualTo(2);
    }

    @Test
    void tocReviewUsesTheSameProfileWithBoundedPatchOutputAndNoPaidRetry() throws Exception {
        // 준비: 기존 profile의 모델/추론 설정과 작업 trace를 유지한다.
        adapter.configureProfile(new jp.co.translacat.novel.infrastructure.ai.NovelAiProfile("verified-profile", "v1", "low", "default", 32768));
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("provider", "openai");
        result.put("model", "expected-model");
        result.put("providerCalls", 1);
        result.put("profileId", "verified-profile");
        result.put("profileVersion", "v1");
        result.put("reasoningEffort", "low");
        result.put("serviceTier", "default");
        result.put("inputTokens", 100);
        result.put("outputTokens", 50);
        result.put("cachedInputTokens", 0);
        result.put("reasoningTokens", 0);
        result.put("metadata", Map.of());
        result.put("output", Map.of("corrections", List.of(Map.of("id", "chapter-4", "translation", "아오야마 레이코의 방문",
                "sourceTerm", "青山玲子", "referenceTranslation", "아오야마 레이코"))));
        response = json.writeValueAsString(result);
        var candidates = List.of(new jp.co.translacat.novel.application.NovelPorts.TitleCandidate("chapter-4", "青山玲子の訪問", "청산 레이코의 방문"));
        var references = List.of(new jp.co.translacat.novel.application.NovelPorts.TitleReference("青山玲子は歩く。", "아오야마 레이코는 걷는다."));
        String trace = "aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa";

        // 실행
        var review = adapter.reviewTitles(trace, "toc:0:1", candidates, references, Map.of(), 3000, "ko");

        // 검증: 본문 maxOutputTokens 설정은 그대로 두고 교정 목록 요청만 작게 제한한다.
        var request = json.readTree(body);
        assertThat(requestedPath).isEqualTo("/internal/v1/model/execute-profile");
        assertThat(traceHeader).isEqualTo(trace);
        assertThat(request.path("profileId").asText()).isEqualTo("verified-profile");
        assertThat(request.path("maxOutputTokens").asInt()).isEqualTo(8192);
        assertThat(adapter.maxOutputTokens()).isEqualTo(32768);
        assertThat(request.path("maxProviderCalls").asInt()).isEqualTo(1);
        assertThat(request.path("strict").asBoolean()).isTrue();
        assertThat(review.corrections()).containsEntry("chapter-4", "아오야마 레이코의 방문");
        assertThat(requests.get()).isEqualTo(1);

        // 실행 및 검증: 신규 제목도 같은 문맥으로 생성하고 제목 전용 출력 한도를 사용한다.
        String raw = "青山玲子の訪問";
        var title = new SourceEpisode.Segment("chapter-4", "toc", 4, 0, 0, raw.length(), raw, raw);
        result.put("output", Map.of("items", List.of(Map.of("id", "s0", "text", "아오야마 레이코의 방문"))));
        response = json.writeValueAsString(result);
        var generated = adapter.translateTitles(trace, "toc:generate:1", List.of(title), List.of(references.getFirst().source()),
                Map.of(), List.of(Map.of("source", references.getFirst().source(), "translation", references.getFirst().translation())),
                3000, jp.co.translacat.novel.domain.TranslationOptions.ResponseShape.ARRAY_V2,
                jp.co.translacat.novel.domain.TranslationOptions.ValidationPolicy.MINIMUM_LETTER_DIGIT_V2,
                jp.co.translacat.novel.domain.TranslationOptions.AnnotationPolicy.NONE, "ko");
        var generatedRequest = json.readTree(body);
        var generatedData = json.readTree(generatedRequest.path("messages").get(0).path("content").asText());
        assertThat(generatedRequest.path("maxOutputTokens").asInt()).isEqualTo(8192);
        assertThat(adapter.maxOutputTokens()).isEqualTo(32768);
        assertThat(generatedData.path("acceptedNeighborTranslations").get(0).path("translation").asText())
                .isEqualTo(references.getFirst().translation());
        assertThat(generated.items()).containsEntry("chapter-4", "아오야마 레이코의 방문");
        assertThat(requests.get()).isEqualTo(2);

        // 실행 및 검증: 잘못된 교정 응답을 재과금으로 보정하지 않는다.
        result.put("output", Map.of("corrections", List.of(Map.of("id", "foreign", "translation", "아오야마 레이코의 방문",
                "sourceTerm", "青山玲子", "referenceTranslation", "아오야마 레이코"))));
        response = json.writeValueAsString(result);
        assertThatThrownBy(() -> adapter.reviewTitles(trace, "toc:0:2", candidates, references, Map.of(), 3000, "ko"))
                .hasMessage("TOC_REVIEW_RESPONSE_INVALID");
        assertThat(requests.get()).isEqualTo(3);
    }

    @Test
    void keyedResponseUsesRequiredPropertiesAndDuplicateJsonKeysCannotOverwriteTranslation() throws Exception {
        response = "{\"provider\":\"openai\",\"model\":\"expected-model\",\"providerCalls\":1,\"inputTokens\":10,\"outputTokens\":20,"
                + "\"output\":{\"s1\":\"개\",\"s0\":\"고양이\"}}";
        var result = adapter.translate("c1111111-1111-4111-8111-111111111111", "keyed:0:0", segments, List.of(), Map.of(), 3000,
                jp.co.translacat.novel.domain.TranslationOptions.ResponseShape.KEYED_V3);
        assertThat(json.readTree(body).path("schemaName").asText()).isEqualTo("novel_segments_keyed_v3");
        assertThat(json.readTree(body).path("responseSchema").path("required")).isEqualTo(json.valueToTree(List.of("s0", "s1")));
        assertThat(result.items().keySet()).containsExactlyElementsOf(segments.stream().map(SourceEpisode.Segment::id).toList());
        response = response.replace("\"s0\":\"고양이\"", "\"s0\":\"고양이\",\"s0\":\"덮어쓰기\"");
        assertThatThrownBy(() -> adapter.translate("c1111111-1111-4111-8111-111111111111", "keyed:0:1", segments, List.of(), Map.of(), 3000,
                jp.co.translacat.novel.domain.TranslationOptions.ResponseShape.KEYED_V3)).hasMessage("AI_RESPONSE_INVALID");
    }
}
