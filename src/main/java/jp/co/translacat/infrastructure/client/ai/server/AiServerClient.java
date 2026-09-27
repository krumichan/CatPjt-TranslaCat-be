package jp.co.translacat.infrastructure.client.ai.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.chat.ai.dto.server.ChatAiReplyRequestDto;
import jp.co.translacat.domain.chat.ai.dto.server.ChatAiReplyResponseDto;
import jp.co.translacat.global.exception.AiServerCommunicationException;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.client.ai.server.dto.*;
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
            var response = this.apiClient.post(url, request, this.basicHeader(), Map.class);

            if (response != null && response.containsKey("translated")) {
                return (List<String>) response.get("translated");
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.error("AI Server communication failed: {}", e.getMessage());
            throw new AiServerCommunicationException("AI Server Error", e);
        }
    }

    private Map<String, String> basicHeader() {
        return Map.of("X-API-KEY", apiKey);
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

    public AiChatTranslationResponse callChatTranslation(
            String text,
            String targetLanguageCode
    ) {
        String url = aiServerUrl + "/api/v1/chat/translate";

        AiChatTranslationRequest request =
                new AiChatTranslationRequest(
                        text,
                        targetLanguageCode
                );

        try {
            return this.apiClient.post(
                    url,
                    request,
                    this.basicHeader(),
                    AiChatTranslationResponse.class
            );
        } catch (Exception e) {
            log.error("AI Server chat translation failed: {}", e.getMessage());

            throw new AiServerCommunicationException(
                    "AI Server Chat Translation Error",
                    e
            );
        }
    }

    public ChatAiReplyResponseDto callChatAiReply(
            ChatAiReplyRequestDto request
    ) {
        String url = aiServerUrl + "/api/v1/chat/ai/reply";

        try {
            return this.apiClient.post(
                    url,
                    request,
                    this.basicHeader(),
                    ChatAiReplyResponseDto.class
            );
        } catch (Exception e) {
            log.error(
                    "AI Server chat reply failed. requestId={}, cause={}",
                    request == null ? null : request.requestId(),
                    e.getMessage()
            );
            throw new AiServerCommunicationException(
                    "AI Server Chat Reply Error",
                    e
            );
        }
    }

}
