package jp.co.translacat.infrastructure.client.ai.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.chat.ai.dto.server.ChatAiReplyRequestDto;
import jp.co.translacat.domain.chat.ai.dto.server.ChatAiReplyResponseDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiDailyWritingGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiPracticeGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiWritingEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiDailyWritingGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiPracticeGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiWritingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.*;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.*;
import jp.co.translacat.global.exception.AiServerCommunicationException;
import jp.co.translacat.global.exception.AiServerFailureCode;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.client.ai.server.dto.*;
import jp.co.translacat.infrastructure.client.legacy.ExternalApiClient;
import jp.co.translacat.infrastructure.client.legacy.ExternalApiClient4xxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerClient {
    private static final String PRACTICE_GENERATION_PATH =
            "/api/v1/language-learning/practice/generate";
    private static final int MAX_SAFE_PRACTICE_ERROR_DETAIL_CHARS = 500;
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

    public AiPracticeGenerationResponseDto callLanguageLearningPracticeGeneration(
            AiPracticeGenerationRequestDto request
    ) {
        String url = aiServerUrl + PRACTICE_GENERATION_PATH;
        try {
            return this.apiClient.postOnceLanguageLearningPractice(
                    url, request, this.basicHeader(), AiPracticeGenerationResponseDto.class
            );
        } catch (Exception e) {
            AiServerFailureCode failureCode = AiServerFailureClassifier.classify(e);
            PracticeHttpFailureDiagnostic diagnostic = practiceHttpFailureDiagnostic(e);
            log.error(
                    "AI Server Reading/Vocabulary generation failed. endpoint={} requestId={} "
                            + "httpStatus={} safeDetail={} failureCode={} causeType={}",
                    PRACTICE_GENERATION_PATH,
                    request == null ? null : request.requestId(),
                    diagnostic.httpStatus(),
                    diagnostic.safeDetail(),
                    failureCode,
                    e.getClass().getSimpleName()
            );
            throw new AiServerCommunicationException(
                    "AI Server Reading/Vocabulary Generation Error: " + failureCode,
                    failureCode,
                    diagnostic.httpStatus(),
                    diagnostic.safeDetail(),
                    e
            );
        }
    }

    private PracticeHttpFailureDiagnostic practiceHttpFailureDiagnostic(Throwable failure) {
        ExternalApiClient4xxException clientError = findCause(
                failure,
                ExternalApiClient4xxException.class
        );
        org.springframework.web.reactive.function.client.WebClientResponseException response =
                clientError == null
                        ? findCause(
                        failure,
                        org.springframework.web.reactive.function.client.WebClientResponseException.class
                )
                        : clientError.getResponseException();
        if (response == null) {
            return new PracticeHttpFailureDiagnostic(null, "none");
        }
        return new PracticeHttpFailureDiagnostic(
                response.getRawStatusCode(),
                safePracticeErrorDetail(response.getResponseBodyAsString())
        );
    }

    private String safePracticeErrorDetail(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "empty";
        }
        try {
            JsonNode detail = objectMapper.readTree(responseBody).path("detail");
            if (detail.isArray()) {
                List<String> issues = new java.util.ArrayList<>();
                for (JsonNode issue : detail) {
                    if (issues.size() == 3) break;
                    issues.add("type=" + safeDiagnosticText(issue.path("type").asText("unknown"))
                            + " loc=" + safeValidationLocation(issue.path("loc"))
                            + " msg=" + safeDiagnosticText(issue.path("msg").asText("unknown")));
                }
                return boundedSafeDetail(issues.isEmpty()
                        ? "validation_error_without_issues"
                        : String.join("; ", issues));
            }
            if (detail.isObject()) {
                String code = safeDiagnosticText(detail.path("code").asText("unknown"));
                String stage = detail.path("stage").asText("");
                String safeStage = Set.of(
                        "PLAN_STRUCTURE", "PLAN_AUTHORITY", "LEXICAL_VALIDATION",
                        "CONTEXT_VALIDATION"
                ).contains(stage) ? " stage=" + stage : "";
                return boundedSafeDetail("code=" + code + safeStage + " message=redacted");
            }
            if (detail.isTextual()) {
                return "text_detail_redacted";
            }
            return "unstructured_json_error";
        } catch (JsonProcessingException ignored) {
            return "unparseable_error_body";
        }
    }

    private String safeValidationLocation(JsonNode location) {
        if (!location.isArray()) return "unknown";
        List<String> segments = new java.util.ArrayList<>();
        for (JsonNode segment : location) {
            if (segment.isTextual() || segment.isIntegralNumber()) {
                segments.add(safeDiagnosticText(segment.asText()));
            }
        }
        return segments.isEmpty() ? "unknown" : String.join(".", segments);
    }

    private String safeDiagnosticText(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').strip();
    }

    private String boundedSafeDetail(String value) {
        return value.length() <= MAX_SAFE_PRACTICE_ERROR_DETAIL_CHARS
                ? value
                : value.substring(0, MAX_SAFE_PRACTICE_ERROR_DETAIL_CHARS) + "...<truncated>";
    }

    private record PracticeHttpFailureDiagnostic(Integer httpStatus, String safeDetail) {
    }

    public AiDailyWritingGenerationResponseDto callLanguageLearningDailyGeneration(
            AiDailyWritingGenerationRequestDto request
    ) {
        String url = aiServerUrl
                + "/api/v1/language-learning/writing/daily/generate";

        try {
            return this.apiClient.postOnce(
                    url,
                    request,
                    this.basicHeader(),
                    AiDailyWritingGenerationResponseDto.class
            );
        } catch (Exception e) {
            log.error(
                    "AI Server language learning daily generation failed. "
                            + "requestId={}, cause={}",
                    request == null ? null : request.requestId(),
                    e.getMessage()
            );
            throw new AiServerCommunicationException(
                    "AI Server Language Learning Daily Generation Error",
                    e
            );
        }
    }

    public AiWritingEvaluationResponseDto callLanguageLearningEvaluation(
            AiWritingEvaluationRequestDto request
    ) {
        String url = aiServerUrl
                + "/api/v1/language-learning/writing/evaluate";

        try {
            return this.apiClient.postOnce(
                    url,
                    request,
                    this.basicHeader(),
                    AiWritingEvaluationResponseDto.class
            );
        } catch (Exception e) {
            log.error(
                    "AI Server language learning evaluation failed. "
                            + "requestId={}, cause={}",
                    request == null ? null : request.requestId(),
                    e.getMessage()
            );
            throw new AiServerCommunicationException(
                    "AI Server Language Learning Evaluation Error",
                    e
            );
        }
    }

    private <T extends Throwable> T findCause(
            Throwable throwable,
            Class<T> type
    ) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private <T, R> R postLanguageLearning(
            String path,
            T request,
            Class<R> responseType,
            String operation
    ) {
        try {
            return apiClient.postOnce(
                    aiServerUrl + path,
                    request,
                    basicHeader(),
                    responseType
            );
        } catch (Exception e) {
            log.error(
                    "AI Server language learning {} failed. cause={}",
                    operation,
                    e.getMessage()
            );
            throw new AiServerCommunicationException(
                    "AI Server Language Learning " + operation + " Error",
                    e
            );
        }
    }

    public AiSpeakingSessionStartResponseDto callSpeakingSessionStart(
            AiSpeakingSessionStartRequestDto request
    ) {
        return postSpeaking(
                "/api/v1/language-learning/speaking/sessions/start",
                request,
                AiSpeakingSessionStartResponseDto.class,
                "Speaking Session Start"
        );
    }

    public AiSpeakingTurnProcessResponseDto callSpeakingTurnProcess(
            AiSpeakingTurnProcessRequestDto request,
            byte[] audioBytes,
            String fileName,
            String contentType
    ) {
        String url = aiServerUrl
                + "/api/v1/language-learning/speaking/turns/process";

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("context", toJson(request))
                .contentType(MediaType.TEXT_PLAIN);

        ByteArrayResource resource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return fileName == null || fileName.isBlank()
                        ? "speaking-audio.wav"
                        : fileName;
            }
        };

        builder.part("audio", resource)
                .filename(resource.getFilename())
                .contentType(MediaType.parseMediaType(
                        contentType == null || contentType.isBlank()
                                ? "application/octet-stream"
                                : contentType
                ));

        try {
            return apiClient.postMultipartOnce(
                    url,
                    builder.build(),
                    basicHeader(),
                    AiSpeakingTurnProcessResponseDto.class
            );
        } catch (Exception e) {
            log.error(
                    "AI Server speaking turn processing failed. "
                            + "requestId={}, sessionId={}, turnIndex={}, cause={}",
                    request == null ? null : request.requestId(),
                    request == null ? null : request.sessionId(),
                    request == null ? null : request.turnIndex(),
                    e.getMessage()
            );
            throw new AiServerCommunicationException(
                    "AI Server Speaking Turn Error",
                    e
            );
        }
    }

    public AiSpeakingConversationResponseDto callSpeakingResponse(
            AiSpeakingTurnProcessRequestDto request
    ) {
        return postSpeaking(
                "/api/v1/language-learning/speaking/turns/respond",
                request,
                AiSpeakingConversationResponseDto.class,
                "Speaking Conversation"
        );
    }

    public AiSpeakingAssistanceResponseDto callSpeakingAssistance(
            AiSpeakingAssistanceRequestDto request
    ) {
        return postSpeaking(
                "/api/v1/language-learning/speaking/assistance",
                request,
                AiSpeakingAssistanceResponseDto.class,
                "Speaking Assistance"
        );
    }

    public AiSpeakingTtsResponseDto callSpeakingTts(
            AiSpeakingTtsRequestDto request
    ) {
        return postSpeaking(
                "/api/v1/language-learning/speaking/tts",
                request,
                AiSpeakingTtsResponseDto.class,
                "Speaking TTS"
        );
    }

    public AiSpeakingEvaluationResponseDto callSpeakingEvaluation(
            AiSpeakingEvaluationRequestDto request
    ) {
        return postSpeaking(
                "/api/v1/language-learning/speaking/evaluate",
                request,
                AiSpeakingEvaluationResponseDto.class,
                "Speaking Evaluation"
        );
    }

    public jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingCoachingResponseDto callSpeakingCoaching(
            jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingCoachingRequestDto request
    ) {
        return postSpeaking(
                "/api/v1/language-learning/speaking/coach",
                request,
                jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingCoachingResponseDto.class,
                "Speaking Session Coaching"
        );
    }

    public byte[] callSpeakingAudio(String audioReference) {
        String url = aiServerUrl
                + "/api/v1/language-learning/speaking/audio/"
                + audioReference;

        try {
            return apiClient.getBytesOnce(url, basicHeader());
        } catch (Exception e) {
            log.error(
                    "AI Server speaking audio download failed. "
                            + "audioReference={}, cause={}",
                    audioReference,
                    e.getMessage()
            );
            throw new AiServerCommunicationException(
                    "AI Server Speaking Audio Error",
                    e
            );
        }
    }

    private <T, R> R postSpeaking(
            String path,
            T request,
            Class<R> responseType,
            String operation
    ) {
        String url = aiServerUrl + path;

        try {
            return apiClient.postOnce(
                    url,
                    request,
                    basicHeader(),
                    responseType
            );
        } catch (Exception e) {
            log.error(
                    "AI Server {} failed. cause={}",
                    operation,
                    e.getMessage()
            );
            throw new AiServerCommunicationException(
                    "AI Server " + operation + " Error",
                    e
            );
        }
    }

}
