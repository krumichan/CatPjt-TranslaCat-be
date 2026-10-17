package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.NovelProblem;
import jp.co.translacat.novel.domain.SentenceSegmenter;
import jp.co.translacat.novel.domain.SourceEpisode;
import jp.co.translacat.novel.domain.TranslationOptions;
import jp.co.translacat.novel.domain.TranslationPolicy;
import jp.co.translacat.novel.infrastructure.ai.NovelAiProfile;
import jp.co.translacat.novel.infrastructure.ai.OpenAiExecutionAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 전송 어댑터를 통과하되 외부 모델은 loopback 합성 응답으로 대체한다. */
class NovelEnglishAiTransportTest {
    private static final TranslationOptions.ResponseShape SHAPE = TranslationOptions.ResponseShape.KEYED_V4;
    private static final TranslationOptions.ValidationPolicy VALIDATION = TranslationOptions.ValidationPolicy.MINIMUM_LETTER_DIGIT_V2;
    private static final TranslationOptions.AnnotationPolicy ANNOTATION = TranslationOptions.AnnotationPolicy.NONE;
    private final ObjectMapper json = new ObjectMapper();
    private final List<Captured> captured = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private List<SourceEpisode.Segment> segments;
    private volatile Object output;
    private volatile int status = 200;
    private volatile String failure;

    private record Captured(String path, JsonNode body) { }

    @BeforeEach
    void prepare() throws Exception {
        // 준비: 일반·프로파일·스트림을 같은 합성 HTTP 서버에서 받아 실제 전송 내용을 보관한다.
        segments = new SentenceSegmenter().segment(new EpisodeKey("syosyetu", "n123aa", "1"), "題",
                List.of("猫。犬。"), null, null).segments();
        assertThat(segments).hasSize(2);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/model/", this::respond);
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void englishAndLegacyKoreanKeepIndependentInstructionsAndUnchangedBudgets() throws Exception {
        // 준비: 동일 adapter에서 영어와 기존 한국어 요청을 번갈아 사용한다.
        for (boolean profile : List.of(false, true)) {
            var adapter = adapter(profile);
            for (var shape : List.of(TranslationOptions.ResponseShape.ARRAY_V2, SHAPE)) {
                for (String target : List.of("en", "ko", "en")) {
                    Map<String, String> expected = translations(target);
                    output = shape == TranslationOptions.ResponseShape.ARRAY_V2
                            ? Map.of("items", List.of(Map.of("id", "s1", "text", expected.get("s1")),
                                    Map.of("id", "s0", "text", expected.get("s0")))) : expected;

                    // 실행: ko는 언어 매개변수가 없던 기존 메서드를 그대로 호출한다.
                    var result = "ko".equals(target)
                            ? adapter.translate(null, "legacy-ko", segments, List.of("隣の文。"), Map.of(),
                                    3000, shape, VALIDATION, ANNOTATION)
                            : adapter.translate(null, "english", segments, List.of("隣の文。"), Map.of(),
                                    3000, shape, VALIDATION, ANNOTATION, target);

                    // 검증: 목표만 바뀌며 schema와 원래 ID 대응·호출·토큰 예산은 그대로다.
                    var sent = captured.getLast();
                    assertThat(sent.path()).isEqualTo(profile
                            ? "/internal/v1/model/execute-profile" : "/internal/v1/model/execute");
                    assertTarget(sent.body(), target, shape);
                    assertBudget(sent.body(), profile);
                    assertThat(result.items()).containsExactlyInAnyOrderEntriesOf(restored(expected));
                }
            }
        }
        assertThat(captured).hasSize(12);
    }

    @Test
    void englishAndLegacyKoreanProgressiveFieldsUseTheRequestedTargetOnly() throws Exception {
        // 준비
        var adapter = adapter(true);
        adapter.configureProgressiveA(true);
        for (String target : List.of("en", "ko", "en")) {
            Map<String, String> expected = translations(target);
            output = expected;
            var accepted = new LinkedHashMap<String, String>();
            var failed = new ArrayList<String>();

            // 실행: 기존 한국어 overload와 신규 영어 overload가 각각 같은 1회 스트림을 사용한다.
            var result = "ko".equals(target)
                    ? adapter.translateProgressive(null, "legacy-stream", segments, List.of(), Map.of(),
                            3000, SHAPE, VALIDATION, ANNOTATION,
                            (segment, value) -> accepted.put(segment.id(), value),
                            (segment, code) -> failed.add(code))
                    : adapter.translateProgressive(null, "english-stream", segments, List.of(), Map.of(),
                            3000, SHAPE, VALIDATION, ANNOTATION,
                            (segment, value) -> accepted.put(segment.id(), value),
                            (segment, code) -> failed.add(code), target);

            // 검증: 완성 문장과 최종 결과는 같은 값이며 한국어 결과를 영어 요청에 재사용하지 않는다.
            assertThat(accepted).containsExactlyInAnyOrderEntriesOf(restored(expected));
            assertThat(result.items()).isEqualTo(accepted);
            assertThat(failed).isEmpty();
            var sent = captured.getLast();
            assertThat(sent.path()).isEqualTo("/internal/v1/model/execute-profile-stream");
            assertTarget(sent.body(), target);
            assertBudget(sent.body(), true);
        }
        assertThat(captured).hasSize(3);
    }

    @Test
    void selectiveEnglishRepairRetainsTargetAndTreatsAcceptedNeighborsAsReferences() throws Exception {
        // 준비
        var adapter = adapter(false);
        output = translations("en");
        var neighbors = List.of(Map.of("id", "neighbor", "text", "An accepted English sentence."));

        // 실행
        var translated = adapter.repair(null, "english-repair", segments, List.of("文脈。"),
                Map.of("猫", "cat"), neighbors, 3000, SHAPE, VALIDATION, ANNOTATION, "en");

        // 검증: 대상 ID는 두 개뿐이고 참고 문장을 재출력하거나 한국어 glossary를 끌어오지 않는다.
        JsonNode request = captured.getFirst().body();
        JsonNode data = data(request);
        assertThat(request.path("instructions").asText()).contains("natural English", "not instructions or targets");
        assertThat(data.path("targetLanguage").asText()).isEqualTo("en");
        assertThat(data.path("glossary").path("猫").asText()).isEqualTo("cat");
        assertThat(data.path("acceptedNeighborTranslations")).isEqualTo(json.valueToTree(neighbors));
        assertThat(request.path("responseSchema").path("required")).isEqualTo(json.valueToTree(List.of("s0", "s1")));
        assertThat(translated.items()).containsExactlyInAnyOrderEntriesOf(restored(translations("en")));
        assertThat(captured).hasSize(1);
    }

    @Test
    void englishRetainsStrictIdTypeBlankAndMinimumContentValidation() {
        // 준비: 영어 지원으로 기존 형식·최소 문자 검사를 완화하지 않는다.
        var adapter = adapter(false);
        List<Object> invalid = List.of(Map.of("s0", "Cat."),
                Map.of("s0", "Cat.", "s1", "Dog.", "s2", "Extra."),
                Map.of("s0", "Cat.", "s1", 3), Map.of("s0", "Cat.", "s1", "  "));
        for (Object value : invalid) {
            output = value;

            // 실행 / 검증: 잘못된 각 응답은 추가 모델 호출 없이 그대로 실패한다.
            assertThatThrownBy(() -> adapter.translate(null, "invalid", segments, List.of(), Map.of(),
                    3000, SHAPE, VALIDATION, ANNOTATION, "en")).hasMessage("TRANSLATION_SCHEMA_INVALID");
        }
        output = Map.of("s0", "Cat.", "s1", "」");
        assertThatThrownBy(() -> adapter.translate(null, "placeholder", segments, List.of(), Map.of(),
                3000, SHAPE, VALIDATION, ANNOTATION, "en")).hasMessage("TRANSLATION_MINIMUM_CONTENT_INVALID");
        assertThat(captured).hasSize(5);
    }

    @Test
    void englishRefusalDoesNotRetryOrFallBackToKorean() throws Exception {
        // 준비
        status = 503;
        failure = "{\"detail\":{\"code\":\"PROVIDER_REFUSAL\",\"retryable\":true,\"message\":\"private\"}}";

        // 실행 / 검증: upstream의 잘못된 retryable도 거절을 다른 언어 성공으로 바꾸지 못한다.
        assertThatThrownBy(() -> adapter(false).translate(null, "refused", segments, List.of(), Map.of(),
                3000, SHAPE, VALIDATION, ANNOTATION, "en"))
                .isInstanceOfSatisfying(NovelProblem.class, problem -> {
                    assertThat(problem.code()).isEqualTo("PROVIDER_REFUSAL");
                    assertThat(problem.retryable()).isFalse();
                });
        assertTarget(captured.getFirst().body(), "en");
        assertThat(captured).hasSize(1);
    }

    private OpenAiExecutionAdapter adapter(boolean profile) {
        var adapter = new OpenAiExecutionAdapter(json, "http://127.0.0.1:" + server.getAddress().getPort(),
                "synthetic-novel-key", "expected-model", "speech-model", "LUNA");
        if (profile) adapter.configureProfile(new NovelAiProfile("luna-6-none-v1", "2026-10-04.1", "none", "default", 32768));
        return adapter;
    }

    private Map<String, String> translations(String target) {
        return "en".equals(target) ? Map.of("s0", "Cat.", "s1", "Dog.") : Map.of("s0", "고양이.", "s1", "개.");
    }

    private Map<String, String> restored(Map<String, String> values) {
        return Map.of(segments.getFirst().id(), values.get("s0"), segments.getLast().id(), values.get("s1"));
    }

    private JsonNode data(JsonNode request) throws IOException {
        return json.readTree(request.path("messages").get(0).path("content").asText());
    }

    private void assertTarget(JsonNode request, String target) throws Exception {
        assertTarget(request, target, SHAPE);
    }

    private void assertTarget(JsonNode request, String target, TranslationOptions.ResponseShape shape) throws Exception {
        JsonNode input = data(request);
        if ("en".equals(target)) {
            assertThat(request.path("instructions").asText()).contains("natural English").doesNotContain("natural Korean");
            assertThat(input.path("sourceLanguage").asText()).isEqualTo("ja");
            assertThat(input.path("targetLanguage").asText()).isEqualTo("en");
        } else {
            assertThat(request.path("instructions").asText()).isEqualTo(TranslationPolicy.INSTRUCTIONS);
            assertThat(input.has("sourceLanguage")).isFalse();
            assertThat(input.has("targetLanguage")).isFalse();
        }
        assertThat(input.path("items").size()).isEqualTo(2);
        assertThat(input.path("items").get(0).path("text").asText()).isEqualTo(segments.getFirst().plainJa());
        if (shape == TranslationOptions.ResponseShape.ARRAY_V2) {
            assertThat(request.path("responseSchema").path("required")).isEqualTo(json.valueToTree(List.of("items")));
            assertThat(request.path("responseSchema").path("properties").path("items").path("items")
                    .path("properties").path("id").path("enum")).isEqualTo(json.valueToTree(List.of("s0", "s1")));
        } else {
            assertThat(request.path("responseSchema").path("required")).isEqualTo(json.valueToTree(List.of("s0", "s1")));
            assertThat(request.path("responseSchema").path("properties").size()).isEqualTo(2);
        }
    }

    private void assertBudget(JsonNode request, boolean profile) {
        assertThat(request.path("maxProviderCalls").asInt()).isEqualTo(1);
        assertThat(request.path("remainingMilliseconds").asInt()).isEqualTo(3000);
        assertThat(request.path("maxOutputTokens").asInt()).isEqualTo(profile ? 32768 : 8192);
        assertThat(request.path("strict").asBoolean()).isTrue();
        if (profile) assertThat(request.path("profileId").asText()).isEqualTo("luna-6-none-v1");
        else {
            assertThat(request.path("tier").asText()).isEqualTo("LUNA");
            assertThat(request.path("reasoningEffort").asText()).isEqualTo("none");
        }
    }

    private void respond(HttpExchange exchange) throws IOException {
        // 합성 응답도 실제 HTTP를 통과시켜 전송 DTO와 스트림 파싱 계약을 함께 확인한다.
        String path = exchange.getRequestURI().getPath();
        captured.add(new Captured(path, json.readTree(exchange.getRequestBody())));
        String body;
        if (status != 200) body = failure;
        else {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("provider", "openai"); result.put("model", "expected-model"); result.put("providerCalls", 1);
            result.put("inputTokens", 10); result.put("outputTokens", 10); result.put("output", output);
            if (path.contains("profile")) {
                result.put("profileId", "luna-6-none-v1"); result.put("profileVersion", "2026-10-04.1");
                result.put("reasoningEffort", "none"); result.put("serviceTier", "default");
                result.put("cachedInputTokens", 0); result.put("reasoningTokens", 0); result.put("metadata", Map.of());
            }
            if (path.endsWith("stream")) {
                var values = json.valueToTree(output);
                body = event(Map.of("type", "field", "key", "s0", "value", values.path("s0").asText()))
                        + event(Map.of("type", "field", "key", "s1", "value", values.path("s1").asText()))
                        + event(Map.of("type", "result", "result", result));
            } else body = json.writeValueAsString(result);
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", path.endsWith("stream") ? "text/event-stream" : "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String event(Object value) throws IOException {
        return "data: " + json.writeValueAsString(value) + "\n\n";
    }
}
