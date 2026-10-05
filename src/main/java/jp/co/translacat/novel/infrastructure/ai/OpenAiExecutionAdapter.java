package jp.co.translacat.novel.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.application.NovelPorts;
import jp.co.translacat.novel.application.RequestTrace;
import jp.co.translacat.novel.application.NovelPorts.Translation;
import jp.co.translacat.novel.application.NovelPorts.Speech;
import jp.co.translacat.novel.domain.NovelProblem;
import jp.co.translacat.novel.domain.SourceEpisode;
import jp.co.translacat.novel.domain.TranslationPolicy;
import jp.co.translacat.novel.domain.TranslationOptions;
import jp.co.translacat.novel.infrastructure.http.BoundedHttp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

@Component
public class OpenAiExecutionAdapter implements NovelPorts.Ai {
    private final ObjectMapper json;
    private final ObjectMapper strictResponses;
    private final URI baseUri;
    private final String apiKey;
    private final String model;
    private final String speechModel;
    private final String tier;
    private NovelAiProfile profile = NovelAiProfile.legacy();
    @Value("${novel.ai.progressive-a:false}")
    private boolean progressiveA;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private static final java.util.concurrent.ScheduledExecutorService STREAM_DEADLINES =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().factory());

    public OpenAiExecutionAdapter(ObjectMapper json, @Value("${novel.ai.base-url}") String baseUrl,
                                  @Value("${novel.ai.api-key}") String apiKey,
                                  @Value("${novel.ai.model}") String model,
                                  @Value("${novel.ai.speech-model}") String speechModel,
                                  @Value("${novel.ai.tier}") String tier) {
        this.json = json;
        this.strictResponses = json.copy().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.baseUri = URI.create(baseUrl);
        boolean loopback = List.of("127.0.0.1", "localhost", "[::1]").contains(baseUri.getHost());
        if (!("https".equals(baseUri.getScheme()) || loopback && "http".equals(baseUri.getScheme())) || baseUri.getHost() == null
                || baseUri.getUserInfo() != null || baseUri.getQuery() != null || baseUri.getFragment() != null
                || !(baseUri.getPath().isEmpty() || baseUri.getPath().equals("/"))) {
            throw new IllegalArgumentException("NOVEL_AI_URL_INVALID");
        }
        // 항상 제공하는 소설 실행 경로에는 전용 인증키가 필요하다. 누락 시 기동을 중단한다.
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("NOVEL_AI_API_KEY_REQUIRED");
        }
        this.apiKey = apiKey;
        this.model = model;
        this.speechModel = speechModel;
        this.tier = tier;
    }

    @Override public String model() { return model; }
    @Override public String speechModel() { return speechModel; }
    @org.springframework.beans.factory.annotation.Autowired
    public void configureProfile(NovelAiProfile profile) { this.profile = profile; }
    public void configureProgressiveA(boolean enabled) { this.progressiveA = enabled; }
    @Override public boolean progressiveAEnabled() { return progressiveA; }
    @Override public int maxOutputTokens() { return profile.id().isBlank() ? 8192 : profile.maxOutputTokens(); }
    @Override public String executionIdentity() {
        if (profile.id().isBlank()) return model + "|legacy|" + effort() + "|8192|standard";
        return String.join("|", model, profile.id(), profile.version(), profile.reasoningEffort(), Integer.toString(profile.maxOutputTokens()), profile.serviceTier())
                + (progressiveA ? "|progressive-a-v1" : "");
    }

    @Override
    public Translation translate(String traceId, List<SourceEpisode.Segment> items,
                                 List<String> context, long remainingMillis) {
        return translate(traceId, items, context, Map.of(), remainingMillis);
    }

    @Override
    public Translation translate(String traceId, List<SourceEpisode.Segment> items,
                                 List<String> context, Map<String, String> glossary, long remainingMillis) {
        return translate(null, traceId, items, context, glossary, remainingMillis);
    }

    @Override
    public Translation translate(String rootTraceId, String callId, List<SourceEpisode.Segment> items,
                                 List<String> context, Map<String, String> glossary, long remainingMillis) {
        return translate(rootTraceId, callId, items, context, glossary, remainingMillis, TranslationOptions.ResponseShape.ARRAY_V2);
    }

    @Override
    public Translation translate(String rootTraceId, String callId, List<SourceEpisode.Segment> items,
                                 List<String> context, Map<String, String> glossary, long remainingMillis,
                                 TranslationOptions.ResponseShape shape) {
        return translate(rootTraceId, callId, items, context, glossary, remainingMillis, shape,
                TranslationOptions.ValidationPolicy.BASIC_V1, TranslationOptions.AnnotationPolicy.NONE);
    }

    @Override
    public Translation translate(String rootTraceId, String callId, List<SourceEpisode.Segment> items,
                                 List<String> context, Map<String, String> glossary, long remainingMillis,
                                 TranslationOptions.ResponseShape shape, TranslationOptions.ValidationPolicy validation,
                                 TranslationOptions.AnnotationPolicy annotation) {
        var prepared = new TranslationRequestFactory().prepare(items, context, glossary, shape, annotation);
        return translatePrepared(rootTraceId, callId, remainingMillis, shape, validation, prepared);
    }

    @Override public Translation repair(String rootTraceId, String callId, List<SourceEpisode.Segment> items,
            List<String> context, Map<String,String> glossary, List<Map<String,String>> acceptedReferences,
            long remainingMillis, TranslationOptions.ResponseShape shape, TranslationOptions.ValidationPolicy validation,
            TranslationOptions.AnnotationPolicy annotation) {
        return translatePrepared(rootTraceId, callId, remainingMillis, shape, validation,
                new TranslationRequestFactory().repair(items, context, glossary, acceptedReferences, shape, annotation));
    }

    private Translation translatePrepared(String rootTraceId, String callId, long remainingMillis,
            TranslationOptions.ResponseShape shape, TranslationOptions.ValidationPolicy validation,
            TranslationRequestFactory.Prepared prepared) {
        var messages = List.of(new AiExecutionDtos.Message("user", encode(prepared.data())));
        Map<String, Object> timings = new java.util.LinkedHashMap<>();
        JsonNode output; String provider; String actualModel; int calls; long inputTokens; long outputTokens;
        if (profile.id().isBlank()) {
            var command = new AiExecutionDtos.ModelCommand(callId, prepared.instructions(), messages, tier, effort(), "low",
                    8192, remainingMillis, 1, prepared.schema(), prepared.schemaName(), true, "NOVEL_EPISODE");
            var response = post("/internal/v1/model/execute", command, AiExecutionDtos.ModelResult.class, remainingMillis, 500_000, rootTraceId);
            var result = response.result();
            timings.putAll(response.timings());
            output = result.output(); provider = result.provider(); actualModel = result.model(); calls = result.providerCalls();
            inputTokens = result.inputTokens(); outputTokens = result.outputTokens();
            timings.put("providerMs", null); timings.put("aiQueueMs", null);
        } else {
            var command = new AiExecutionDtos.ProfileCommand(callId, prepared.instructions(), messages, profile.id(),
                    profile.maxOutputTokens(), remainingMillis, 1, prepared.schema(), prepared.schemaName(), true, "NOVEL_EPISODE");
            var response = post("/internal/v1/model/execute-profile", command, AiExecutionDtos.ProfileResult.class, remainingMillis, 1_500_000, rootTraceId);
            var result = response.result();
            if (!profile.id().equals(result.profileId()) || !profile.version().equals(result.profileVersion())
                    || !profile.reasoningEffort().equals(result.reasoningEffort()) || !profile.serviceTier().equals(result.serviceTier())
                    || result.cachedInputTokens() < 0 || result.reasoningTokens() < 0) throw new NovelProblem("AI_PROFILE_IDENTITY_MISMATCH", 502);
            timings.putAll(response.timings());
            timings.put("cachedInputTokens", result.cachedInputTokens()); timings.put("reasoningTokens", result.reasoningTokens());
            copyProviderTimings(result.metadata(), timings);
            output = result.output(); provider = result.provider(); actualModel = result.model(); calls = result.providerCalls();
            inputTokens = result.inputTokens(); outputTokens = result.outputTokens();
        }
        long validationStarted = System.nanoTime();
        verifyProvider(provider, actualModel, model, calls);
        if (inputTokens < 0 || outputTokens < 0) throw new NovelProblem("AI_USAGE_INVALID", 502);
        var restored = prepared.batch().restore(output, shape, validation);
        timings.put("validationMs", RequestTrace.elapsed(validationStarted)); timings.put("providerTtftMs", null);
        return new Translation(restored, provider, actualModel, inputTokens, outputTokens, timings);
    }

    @Override
    public Translation translateProgressive(String rootTraceId, String callId, List<SourceEpisode.Segment> items,
                                            List<String> context, Map<String, String> glossary, long remainingMillis,
                                            TranslationOptions.ResponseShape shape, TranslationOptions.ValidationPolicy validation,
                                            TranslationOptions.AnnotationPolicy annotation,
                                            BiConsumer<SourceEpisode.Segment, String> onSentence) {
        return translateProgressive(rootTraceId, callId, items, context, glossary, remainingMillis, shape, validation, annotation,
                onSentence, (segment, code) -> { throw new NovelProblem(code, 502); });
    }

    @Override public Translation translateProgressive(String rootTraceId, String callId, List<SourceEpisode.Segment> items,
            List<String> context, Map<String,String> glossary, long remainingMillis,
            TranslationOptions.ResponseShape shape, TranslationOptions.ValidationPolicy validation,
            TranslationOptions.AnnotationPolicy annotation, BiConsumer<SourceEpisode.Segment,String> onSentence,
            BiConsumer<SourceEpisode.Segment,String> onFailure) {
        if (!progressiveA || profile.id().isBlank() || shape != TranslationOptions.ResponseShape.KEYED_V4)
            throw new NovelProblem("PROGRESSIVE_PROFILE_INVALID", 503);
        var prepared = new TranslationRequestFactory().prepare(items, context, glossary, shape, annotation);
        var command = new AiExecutionDtos.ProfileCommand(callId, prepared.instructions(),
                List.of(new AiExecutionDtos.Message("user", encode(prepared.data()))), profile.id(),
                profile.maxOutputTokens(), remainingMillis, 1, prepared.schema(), prepared.schemaName(), true, "NOVEL_EPISODE");
        if (remainingMillis < 1 || remainingMillis > 300_000) throw new NovelProblem("JOB_DEADLINE_EXCEEDED", 504);
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve("/internal/v1/model/execute-profile-stream"))
                .timeout(Duration.ofMillis(remainingMillis)).header("Content-Type", "application/json")
                .header("Accept", "text/event-stream").header("X-API-KEY", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(encode(command)));
        if (rootTraceId != null) builder.header("X-Novel-Trace-Id", rootTraceId);
        long started = System.nanoTime();
        try {
            HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            // InputStream 응답 본문에는 HttpRequest.timeout이 적용되지 않는다. 전체 기한에 연결을 닫는다.
            try (InputStream boundedBody = new DeadlineStream(response.body(), Math.max(1,
                    remainingMillis - (long) RequestTrace.elapsed(started)))) {
            if (response.statusCode() != 200) {
                try (InputStream body = boundedBody) {
                    throw normalizedFailure(response.statusCode(), body.readNBytes(65_536));
                }
            }
            Map<String, String> published = new java.util.LinkedHashMap<>();
            var seen = new java.util.HashSet<String>();
            var failed = new java.util.HashSet<String>();
            Double firstFieldMs = null;
            AiExecutionDtos.ProfileResult result = null;
            try (var reader = new BufferedReader(new InputStreamReader(
                    new FilterInputStream(boundedBody) {
                        private long bytes;
                        @Override public int read() throws IOException {
                            int value = super.read();
                            if (value >= 0 && ++bytes > 5_000_000) throw new IOException("SSE limit");
                            return value;
                        }
                        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                            int count = in.read(buffer, offset, Math.min(length, 8192));
                            if (count > 0 && (bytes += count) > 5_000_000) throw new IOException("SSE limit");
                            return count;
                        }
                    }, StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ")) continue;
                    JsonNode event = strictResponses.readTree(line.substring(6));
                    String type = event.path("type").asText();
                    if ("error".equals(type)) {
                        String code = event.path("code").asText("PROGRESSIVE_EXECUTION_FAILED");
                        throw new NovelProblem(code.matches("[A-Z][A-Z0-9_]{1,80}") ? code : "PROGRESSIVE_EXECUTION_FAILED", 502);
                    }
                    if ("field".equals(type) || "field-error".equals(type)) {
                        if (result != null) throw new NovelProblem("PROGRESSIVE_EVENT_ORDER_INVALID", 502);
                        String wireKey = event.path("key").asText();
                        if (!wireKey.matches("s(0|[1-9][0-9]{0,5})") || !seen.add(wireKey))
                            throw new NovelProblem("PROGRESSIVE_EVENT_ORDER_INVALID", 502);
                        int index = Integer.parseInt(wireKey.substring(1));
                        if (index >= items.size()) throw new NovelProblem("PROGRESSIVE_EVENT_ORDER_INVALID", 502);
                        SourceEpisode.Segment segment = items.get(index);
                        if ("field-error".equals(type)) {
                            String code = event.path("code").asText();
                            if (!List.of("PROGRESSIVE_VALUE_BLANK", "PROGRESSIVE_KEY_MISSING").contains(code))
                                throw new NovelProblem("PROGRESSIVE_EVENT_INVALID", 502);
                            failed.add(segment.id()); onFailure.accept(segment, code); continue;
                        }
                        if (!event.path("value").isTextual()) throw new NovelProblem("PROGRESSIVE_EVENT_INVALID", 502);
                        String value = event.path("value").textValue();
                        try {
                            if (jp.co.translacat.novel.domain.SourceText.isBlank(value) || value.length() > 12_000 || value.indexOf('\0') >= 0)
                                throw new NovelProblem("TRANSLATION_SCHEMA_INVALID", 502);
                            jp.co.translacat.novel.domain.TranslationContentGuard.validate(List.of(segment), Map.of(segment.id(), value), validation);
                        } catch (NovelProblem invalid) { failed.add(segment.id()); onFailure.accept(segment, invalid.code()); continue; }
                        if (firstFieldMs == null && event.path("providerElapsedMs").isNumber())
                            firstFieldMs = event.path("providerElapsedMs").doubleValue();
                        onSentence.accept(segment, value);
                        published.put(segment.id(), value);
                    } else if ("result".equals(type)) {
                        if (result != null) throw new NovelProblem("PROGRESSIVE_EVENT_ORDER_INVALID", 502);
                        result = strictResponses.treeToValue(event.path("result"), AiExecutionDtos.ProfileResult.class);
                    } else throw new NovelProblem("PROGRESSIVE_EVENT_INVALID", 502);
                }
            }
            if (result == null || !profile.id().equals(result.profileId()) || !profile.version().equals(result.profileVersion())
                    || !profile.reasoningEffort().equals(result.reasoningEffort()) || !profile.serviceTier().equals(result.serviceTier())
                    || result.cachedInputTokens() < 0 || result.reasoningTokens() < 0)
                throw new NovelProblem("AI_PROFILE_IDENTITY_MISMATCH", 502);
            verifyProvider(result.provider(), result.model(), model, result.providerCalls());
            if (result.inputTokens() < 0 || result.outputTokens() < 0) throw new NovelProblem("AI_USAGE_INVALID", 502);
            long validationStarted = System.nanoTime();
            // 최종 전체 응답과 이미 수용한 각 값의 동일성을 검사한다. 식별된 구멍은 별도 실패로 남긴다.
            if (!result.output().isObject() || seen.size() != items.size()) throw new NovelProblem("PROGRESSIVE_FINAL_MISMATCH", 502);
            var restored = new java.util.LinkedHashMap<String,String>();
            var fields = result.output().fieldNames();
            while (fields.hasNext()) if (!seen.contains(fields.next())) throw new NovelProblem("PROGRESSIVE_FINAL_MISMATCH", 502);
            for (int index = 0; index < items.size(); index++) {
                String id = items.get(index).id();
                if (failed.contains(id)) continue;
                JsonNode value = result.output().path("s" + index);
                if (!value.isTextual() || !value.textValue().equals(published.get(id))) throw new NovelProblem("PROGRESSIVE_FINAL_MISMATCH", 502);
                restored.put(id, value.textValue());
            }
            Map<String, Object> timings = new java.util.LinkedHashMap<>();
            timings.put("httpWaitMs", RequestTrace.elapsed(started));
            timings.put("validationMs", RequestTrace.elapsed(validationStarted));
            timings.put("cachedInputTokens", result.cachedInputTokens());
            timings.put("reasoningTokens", result.reasoningTokens());
            copyProviderTimings(result.metadata(), timings);
            timings.put("providerTtftMs", result.metadata() == null ? null : result.metadata().get("providerFirstTokenMs"));
            timings.put("providerFirstSentenceMs", firstFieldMs);
            return new Translation(restored, result.provider(), result.model(), result.inputTokens(), result.outputTokens(), timings);
            }
        } catch (NovelProblem problem) {
            throw problem;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new NovelProblem("PROVIDER_TIMEOUT", 504, true, 0);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NovelProblem("PROVIDER_INTERRUPTED", 503);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new NovelProblem("AI_RESPONSE_INVALID", 502);
        } catch (IOException exception) {
            if (RequestTrace.elapsed(started) >= remainingMillis) throw new NovelProblem("PROVIDER_TIMEOUT", 504);
            throw new NovelProblem("AI_TRANSPORT_UNAVAILABLE", 503, true, 0);
        }
    }

    private static final class DeadlineStream extends FilterInputStream {
        private final java.util.concurrent.ScheduledFuture<?> expiry;
        DeadlineStream(InputStream input, long millis) {
            super(input);
            expiry = STREAM_DEADLINES.schedule(() -> { try { input.close(); } catch (IOException ignored) { } }, millis, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        @Override public void close() throws IOException { expiry.cancel(false); super.close(); }
    }

    private String effort() {
        return switch (tier) { case "LUNA" -> "none"; case "NANO", "MINI" -> "low";
            default -> throw new NovelProblem("MODEL_TIER_NOT_ALLOWED", 503); };
    }

    private void copyProviderTimings(Map<String, Object> metadata, Map<String, Object> target) {
        for (String name : List.of("queueMs", "providerMs", "decodeMs", "totalMs")) {
            Object value = metadata == null ? null : metadata.get(name);
            target.put(name.equals("queueMs") ? "aiQueueMs" : name.equals("decodeMs") ? "aiDecodeMs" : name.equals("totalMs") ? "aiTotalMs" : name,
                    value instanceof Number number && Double.isFinite(number.doubleValue()) && number.doubleValue() >= 0 ? number : null);
        }
        Object requestId = metadata == null ? null : metadata.get("providerRequestId");
        if (requestId instanceof String value && value.matches("[A-Za-z0-9_-]{1,150}")) target.put("providerRequestId", value);
        if (metadata != null && metadata.get("rateLimits") instanceof Map<?, ?> limits) {
            Map<String, String> safe = new java.util.LinkedHashMap<>();
            for (String header : List.of("x-ratelimit-limit-requests", "x-ratelimit-remaining-requests", "x-ratelimit-reset-requests",
                    "x-ratelimit-limit-tokens", "x-ratelimit-remaining-tokens", "x-ratelimit-reset-tokens")) {
                Object value = limits.get(header);
                if (value instanceof String text && text.matches("[A-Za-z0-9 .:+-]{1,100}")) safe.put(header, text);
            }
            target.put("rateLimits", safe);
        }
    }

    @Override
    public Speech synthesize(String requestId, String text, String language, long remainingMillis) {
        return synthesize(null, requestId, text, language, remainingMillis);
    }

    @Override
    public Speech synthesize(String rootTraceId, String requestId, String text, String language, long remainingMillis) {
        AiExecutionDtos.SpeechCommand command = new AiExecutionDtos.SpeechCommand(requestId, text, "marin",
                language, "NORMAL", remainingMillis, 1);
        var response = post("/internal/v1/speech/synthesize", command, AiExecutionDtos.SpeechResult.class, remainingMillis, 8_000_000, rootTraceId);
        AiExecutionDtos.SpeechResult result = response.result();
        verifyProvider(result.provider(), result.model(), speechModel, result.providerCalls());

        // 이 계약은 완성 WAV 청크다. 바이트 서명까지 검사해 HTML 오류 응답의 재생을 막는다.
        try {
            byte[] audio = Base64.getDecoder().decode(result.audioBase64());
            if (!"audio/wav".equals(result.contentType()) || audio.length < 44 || audio.length > 5_000_000
                    || audio[0] != 'R' || audio[1] != 'I' || audio[2] != 'F' || audio[3] != 'F'
                    || audio[8] != 'W' || audio[9] != 'A' || audio[10] != 'V' || audio[11] != 'E'
                    || result.durationSeconds() != null && (!Double.isFinite(result.durationSeconds()) || result.durationSeconds() < 0)) {
                throw new NovelProblem("AUDIO_RESPONSE_INVALID", 502);
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new NovelProblem("AUDIO_RESPONSE_INVALID", 502);
        }
        return new Speech(result.audioBase64(), result.contentType(), result.durationSeconds(), result.provider(), result.model(), response.timings());
    }

    private void verifyProvider(String provider, String actualModel, String expectedModel, int calls) {
        if (!"openai".equalsIgnoreCase(provider) || !expectedModel.equals(actualModel) || calls != 1) {
            throw new NovelProblem("AI_PROVIDER_IDENTITY_MISMATCH", 502);
        }
    }

    private record Response<T>(T result, Map<String, Object> timings) {}
    private <T> Response<T> post(String path, Object command, Class<T> type, long remainingMillis, int maxBytes, String traceId) {
        if (remainingMillis < 1 || remainingMillis > 300_000) {
            throw new NovelProblem("JOB_DEADLINE_EXCEEDED", 504);
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                    .timeout(Duration.ofMillis(remainingMillis)).header("Content-Type", "application/json")
                    .header("X-API-KEY", apiKey).POST(HttpRequest.BodyPublishers.ofString(encode(command)));
            if (traceId != null) builder.header("X-Novel-Trace-Id", traceId);
            HttpRequest request = builder.build();
            long httpStarted = System.nanoTime();
            HttpResponse<byte[]> response = BoundedHttp.send(client, request, maxBytes, remainingMillis);
            double httpMs = RequestTrace.elapsed(httpStarted);
            if (response.statusCode() != 200) {
                throw normalizedFailure(response.statusCode(), response.body());
            }
            long decodeStarted = System.nanoTime();
            T result = strictResponses.readValue(response.body(), type);
            return new Response<>(result, Map.of("httpWaitMs", httpMs, "httpDecodeMs", RequestTrace.elapsed(decodeStarted)));
        } catch (NovelProblem problem) {
            throw problem;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new NovelProblem("PROVIDER_TIMEOUT", 504, true, 0);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NovelProblem("PROVIDER_INTERRUPTED", 503);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new NovelProblem("AI_RESPONSE_INVALID", 502);
        } catch (java.io.IOException exception) {
            throw new NovelProblem("AI_TRANSPORT_UNAVAILABLE", 503, true, 0);
        }
    }

    private NovelProblem normalizedFailure(int status, byte[] bytes) {
        // 외부 오류 message/본문은 전파하지 않고 승인된 코드와 Retry-After만 읽는다.
        try {
            JsonNode detail = json.readTree(bytes).path("detail");
            String code = detail.path("code").asText("");
            if (!code.matches("[A-Z][A-Z0-9_]{1,80}")) {
                code = "AI_EXECUTION_FAILED";
            }
            boolean transientFailure = status == 429 || status == 503 || status == 504;
            boolean retryable = transientFailure && detail.path("retryable").asBoolean(false)
                    && !code.contains("REFUS") && !code.contains("CONFIGURATION");
            long delay = detail.has("retryAfterSeconds")
                    ? Math.clamp(detail.path("retryAfterSeconds").asLong(0), 0, 86_400) * 1000
                    : Math.clamp(detail.path("retryAfterMilliseconds").asLong(0), 0, 86_400_000);
            return new NovelProblem(code, status >= 500 ? 502 : status, retryable, delay);
        } catch (Exception exception) {
            return new NovelProblem("AI_EXECUTION_FAILED", 502);
        }
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception exception) {
            throw new NovelProblem("AI_REQUEST_INVALID", 500);
        }
    }
}
