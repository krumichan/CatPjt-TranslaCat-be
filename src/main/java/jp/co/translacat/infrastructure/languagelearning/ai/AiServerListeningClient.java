package jp.co.translacat.infrastructure.languagelearning.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.ai.port.ListeningAiClient;
import jp.co.translacat.domain.languagelearning.listening.support.ListeningAiException;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.infrastructure.client.legacy.ExternalApiClient;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class AiServerListeningClient implements ListeningAiClient {

    private final ExternalApiClient apiClient;
    private final ObjectMapper objectMapper;

    @Value("${ai-server.url}")
    private String aiServerUrl;

    @Value("${ai-server.api-key}")
    private String apiKey;

    @Override
    public AiListeningContract.GenerationResponse generateSet(
            AiListeningContract.GenerationRequest request
    ) {
        return post(
                "/api/v1/language-learning/listening/sets/generate",
                request,
                AiListeningContract.GenerationResponse.class,
                "GENERATION",
                null
        );
    }

    @Override
    public AiListeningContract.TtsResponse synthesize(
            AiListeningContract.TtsRequest request
    ) {
        return post(
                "/api/v1/language-learning/listening/tts",
                request,
                AiListeningContract.TtsResponse.class,
                "TTS",
                request == null ? null : request.itemId()
        );
    }

    @Override
    public byte[] getAudio(String audioReference) {
        try {
            String reference = UriUtils.encodePathSegment(
                    audioReference,
                    StandardCharsets.UTF_8
            );
            return apiClient.getBytesOnce(
                    aiServerUrl
                            + "/api/v1/language-learning/listening/audio/"
                            + reference,
                    headers()
            );
        } catch (RuntimeException exception) {
            throw map(exception, "TTS", null);
        }
    }

    @Override
    public AiListeningContract.EvaluationResponse evaluateDictation(
            AiListeningContract.DictationRequest request
    ) {
        return post(
                "/api/v1/language-learning/listening/evaluate/dictation",
                request,
                AiListeningContract.EvaluationResponse.class,
                "DICTATION",
                request == null ? null : request.itemId()
        );
    }

    @Override
    public AiListeningContract.EvaluationResponse evaluateInterpretation(
            AiListeningContract.InterpretationRequest request
    ) {
        return post(
                "/api/v1/language-learning/listening/evaluate/interpretation",
                request,
                AiListeningContract.EvaluationResponse.class,
                "INTERPRETATION",
                request == null ? null : request.itemId()
        );
    }

    @Override
    public AiListeningContract.EvaluationResponse evaluateRepeat(
            AiListeningContract.RepeatRequest request,
            byte[] audioBytes,
            String fileName,
            String contentType
    ) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("context", json(request))
                .contentType(MediaType.APPLICATION_JSON);
        ByteArrayResource resource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return fileName == null || fileName.isBlank()
                        ? "listening-repeat.wav"
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
                    aiServerUrl
                            + "/api/v1/language-learning/listening/evaluate/repeat",
                    builder.build(),
                    headers(),
                    AiListeningContract.EvaluationResponse.class
            );
        } catch (RuntimeException exception) {
            throw map(
                    exception,
                    "STT",
                    request == null ? null : request.itemId()
            );
        }
    }

    @Override
    public AiListeningContract.RecommendationExplanationResponse
    explainRecommendation(
            AiListeningContract.RecommendationExplanationRequest request
    ) {
        return post(
                "/api/v1/language-learning/listening/recommendations/explain",
                request,
                AiListeningContract.RecommendationExplanationResponse.class,
                "EXPLANATION",
                null
        );
    }

    private <T, R> R post(
            String path,
            T request,
            Class<R> responseType,
            String stage,
            Long resourceId
    ) {
        try {
            return apiClient.postOnce(
                    aiServerUrl + path,
                    request,
                    headers(),
                    responseType
            );
        } catch (RuntimeException exception) {
            throw map(exception, stage, resourceId);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw map(exception, "STT", null);
        }
    }

    private Map<String, String> headers() {
        return Map.of("X-API-KEY", apiKey);
    }

    private ListeningAiException map(
            Throwable exception,
            String stage,
            Long resourceId
    ) {
        Throwable current = exception;
        while (current.getCause() != null && current != current.getCause()) {
            if (current instanceof WebClientResponseException
                    || current instanceof TimeoutException
                    || current instanceof java.net.SocketTimeoutException) {
                break;
            }
            current = current.getCause();
        }

        boolean retryable = current instanceof TimeoutException
                || current instanceof java.net.SocketTimeoutException;
        Duration retryAfter = Duration.ofSeconds(1);
        String resolvedCode = errorCode(stage);
        String resolvedStage = stage;
        String resolvedMessage = "AI Listening " + stage + " 호출에 실패했습니다.";
        if (current instanceof WebClientResponseException response) {
            int status = response.getStatusCode().value();
            retryable = status == 429 || status >= 500;
            if (status == 429) {
                retryAfter = parseRetryAfter(
                        response.getHeaders().getFirst("Retry-After")
                );
            }
            try {
                JsonNode detail = objectMapper
                        .readTree(response.getResponseBodyAsString())
                        .path("detail");
                resolvedCode = text(detail, "code", resolvedCode);
                resolvedStage = text(detail, "failedStage", resolvedStage);
                resolvedMessage = text(detail, "message", resolvedMessage);
                if (detail.has("retryable")
                        && detail.get("retryable").isBoolean()) {
                    retryable = retryable
                            || detail.get("retryable").booleanValue();
                }
            } catch (RuntimeException | JsonProcessingException ignored) {
                // Provider-independent fallback values are retained.
            }
        }
        return new ListeningAiException(
                resolvedMessage,
                resolvedCode,
                resolvedStage,
                retryable,
                retryAfter,
                resourceId,
                exception
        );
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isTextual() || value.asText().isBlank()
                ? fallback
                : value.asText();
    }

    private String errorCode(String stage) {
        return switch (stage) {
            case "GENERATION" ->
                    LanguageLearningErrorCode.AI_GENERATION_FAILED;
            case "TTS" -> LanguageLearningErrorCode.AI_TTS_FAILED;
            case "STT" -> LanguageLearningErrorCode.AI_STT_FAILED;
            case "EXPLANATION" ->
                    LanguageLearningErrorCode.AI_EXPLANATION_FAILED;
            default -> LanguageLearningErrorCode.AI_EVALUATION_FAILED;
        };
    }

    private Duration parseRetryAfter(String value) {
        if (value == null) {
            return Duration.ofSeconds(1);
        }
        try {
            return Duration.ofSeconds(
                    Math.max(1, Math.min(60, Long.parseLong(value)))
            );
        } catch (NumberFormatException ignored) {
            return Duration.ofSeconds(1);
        }
    }
}
