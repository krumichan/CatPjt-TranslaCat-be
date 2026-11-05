package jp.co.translacat.infrastructure.client.ai.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos;
import jp.co.translacat.global.exception.AiServerCommunicationException;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.client.ai.server.dto.AiReceiptAnalysisOptions;
import jp.co.translacat.infrastructure.client.ai.server.dto.AiReceiptAnalysisResponse;
import jp.co.translacat.infrastructure.client.ai.server.dto.AiReceiptRuntimeIdentity;
import jp.co.translacat.infrastructure.client.legacy.ExternalApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerClient {
    private final ExternalApiClient apiClient;
    private final ObjectMapper objectMapper;

    @Value("${ai-server.url}")
    private String aiServerUrl;

    @Value("${ai-server.api-key}")
    private String apiKey;

    @Value("${receipt-runtime.expected-run-id:}")
    private String expectedReceiptRunId;

    @Value("${receipt-runtime.expected-source-fingerprint:}")
    private String expectedReceiptSourceFingerprint;

    public String callFileConversion(MultipartFile file) {
        String url = aiServerUrl + "/api/v1/stt/transcribe";

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", file.getResource()).filename(file.getOriginalFilename());

        try {
            var response = this.apiClient.postMultipart(url, builder.build(), this.basicHeader(), Map.class);
            if (response != null) {
                return String.valueOf(response.get("text"));
            }

            return "";
        } catch (Exception e) {
            log.error("AI Server communication failed: {}", e.getMessage());
            throw new AiServerCommunicationException("AI Server Error", e);
        }
    }

    public List<String> callBatchTranslation(List<String> texts, String type) {
        String url = aiServerUrl + "/api/v1/translate/batch";

        Map<String, Object> request = new HashMap<>();
        request.put("texts", texts);
        request.put("type", type);

        try {
            // 유료 번역 재시도는 업무 소유자가 결정한다. 이 HTTP 계층은 한 번만 전송한다.
            var response = this.apiClient.postOnce(url, request, this.basicHeader(), Map.class);
            if (response == null || !(response.get("translated") instanceof List<?> translated)
                    || translated.size() != texts.size()) {
                throw new IllegalStateException("Translation response count is invalid.");
            }
            List<String> validated = new ArrayList<>();
            for (int i = 0; i < translated.size(); i++) {
                Object value = translated.get(i);
                if (!(value instanceof String text) || text.length() > 100_000
                        || (!texts.get(i).isBlank() && text.isBlank())) {
                    throw new IllegalStateException("Translation response text is invalid.");
                }
                validated.add(text);
            }
            return List.copyOf(validated);
        } catch (Exception e) {
            log.error("Novel legacy AI request failed: type={}", e.getClass().getSimpleName());
            throw new AiServerCommunicationException("Novel AI translation failed", "NOVEL_TRANSLATION_FAILED",
                    false, 502, null);
        }
    }

    private Map<String, String> basicHeader() {
        return Map.of("X-API-KEY", apiKey);
    }

    public SpendingAnalysisDtos.AiResponse callSpendingAnalysis(SpendingAnalysisDtos.AiRequest request) {
        // 두 단계 의미 검토의 대기 예산은 이 기능에만 적용한다. 재시도는 하지 않는다.
        long started = System.nanoTime();
        String phase = "BUILD_REQUEST";
        int upstreamStatus = 0;
        try {
            var client = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
            var message = java.net.http.HttpRequest.newBuilder(java.net.URI.create(aiServerUrl + "/api/v1/account-book/spending-analysis"))
                    .timeout(java.time.Duration.ofSeconds(250)).header("Content-Type", "application/json")
                    .header("X-API-KEY", apiKey).POST(java.net.http.HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request))).build();
            phase = "SEND_REQUEST";
            var response = client.send(message, java.net.http.HttpResponse.BodyHandlers.ofString());
            upstreamStatus = response.statusCode();
            phase = "CHECK_RESPONSE";
            if (response.statusCode() != 200 || response.body().length() > 1_000_000) {
                throw new IllegalStateException("Spending analysis response is unavailable");
            }
            phase = "DECODE_RESPONSE";
            return objectMapper.readValue(response.body(), SpendingAnalysisDtos.AiResponse.class);
        } catch (InterruptedException error) {
            logSpendingAnalysisFailure(phase, upstreamStatus, started, error);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Spending analysis was interrupted");
        } catch (java.io.IOException error) {
            logSpendingAnalysisFailure(phase, upstreamStatus, started, error);
            throw new IllegalStateException("Spending analysis communication failed");
        } catch (RuntimeException error) {
            logSpendingAnalysisFailure(phase, upstreamStatus, started, error);
            throw error;
        }
    }

    private static void logSpendingAnalysisFailure(String phase, int status, long started, Exception error) {
        // 키·URL·본문·예외 메시지·stacktrace 대신 고정 경계와 클래스명만 남긴다.
        String causeType = error.getCause() == null ? "NONE" : error.getCause().getClass().getSimpleName();
        log.warn("Spending analysis failed: phase={}, upstreamStatus={}, elapsedMs={}, exceptionType={}, causeType={}",
                phase, status, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                error.getClass().getSimpleName(), causeType);
    }

    public AiReceiptAnalysisResponse callReceiptAnalysis(
            MultipartFile file,
            AiReceiptAnalysisOptions options
    ) {
        String url = aiServerUrl + "/api/v1/account-book/receipts/analyze";
        String traceId = UUID.randomUUID().toString();
        AiReceiptRuntimeIdentity preflight = callReceiptRuntimeIdentity();
        assertExpectedReceiptRuntime(preflight);

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", file.getResource())
                .filename(file.getOriginalFilename());
        builder.part("trace_id", traceId)
                .contentType(MediaType.TEXT_PLAIN);

        if (options != null) {
            builder.part("options", toJson(options))
                    .contentType(MediaType.TEXT_PLAIN);
        }

        try {
            AiReceiptAnalysisResponse response = this.apiClient.postMultipart(
                    url,
                    builder.build(),
                    this.basicHeader(),
                    AiReceiptAnalysisResponse.class
            );
            if (response != null && (response.analysisTraceId() == null
                    || response.analysisTraceId().isBlank())) {
                return new AiReceiptAnalysisResponse(
                        response.receipts(),
                        response.receiptCount(),
                        response.warnings(),
                        response.ocrEngine(),
                        response.usedAi(),
                        traceId,
                        response.runtimeIdentity()
                );
            }
            if (response == null || response.runtimeIdentity() == null
                    || !sameReceiptRuntime(preflight, response.runtimeIdentity())) {
                throw new IllegalStateException("Receipt analysis runtime identity changed after preflight.");
            }
            return response;
        } catch (Exception e) {
            log.error("AI Server receipt analysis failed: type={}", e.getClass().getSimpleName());

            throw new AiServerCommunicationException("AI Server Receipt Analysis Error", e);
        }
    }

    public AiReceiptRuntimeIdentity callReceiptRuntimeIdentity() {
        try {
            AiReceiptRuntimeIdentity identity = apiClient.getOnce(
                    aiServerUrl + "/api/v1/account-book/receipts/runtime-identity",
                    basicHeader(), AiReceiptRuntimeIdentity.class);
            if (identity == null || blank(identity.runId()) || blank(identity.sourceFingerprint()))
                throw new IllegalStateException("AI receipt runtime identity is incomplete.");
            return identity;
        } catch (Exception e) {
            throw new AiServerCommunicationException("AI Server Receipt Runtime Preflight Error", e);
        }
    }

    private void assertExpectedReceiptRuntime(AiReceiptRuntimeIdentity identity) {
        if (!blank(expectedReceiptRunId) && !expectedReceiptRunId.equals(identity.runId()))
            throw new IllegalStateException("AI receipt runtime run id does not match the launcher.");
        if (!blank(expectedReceiptSourceFingerprint)
                && !expectedReceiptSourceFingerprint.equals(identity.sourceFingerprint()))
            throw new IllegalStateException("AI receipt runtime source fingerprint does not match the launcher.");
    }

    private boolean sameReceiptRuntime(
            AiReceiptRuntimeIdentity before, AiReceiptRuntimeIdentity after) {
        return before.runId().equals(after.runId())
                && before.sourceFingerprint().equals(after.sourceFingerprint())
                && java.util.Objects.equals(before.processId(), after.processId())
                && java.util.Objects.equals(before.startedAt(), after.startedAt());
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("AI Server 요청 옵션 생성에 실패했습니다.");
        }
    }

}
