package jp.co.translacat.infrastructure.client.ai.server.voice;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.translacat.domain.voice.model.VoiceTranslationRetryContext;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.client.ai.server.voice.dto.AiVoiceTranslationRetryRequest;
import jp.co.translacat.infrastructure.client.ai.server.voice.dto.AiVoiceTranslationRetryResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class VoiceAiTranslationRetryClient {

    private final WebClient webClient;

    @Value("${ai-server.url}")
    private String aiServerUrl;

    @Value("${ai-server.api-key}")
    private String aiServerApiKey;

    @Value("${translacat.voice.ai-retry-timeout-ms:8000}")
    private long retryTimeoutMs;

    public AiVoiceTranslationRetryResponse retry(
            String sessionId,
            Long segmentId,
            VoiceTranslationRetryContext context
    ) {
        AiVoiceTranslationRetryRequest request =
                new AiVoiceTranslationRetryRequest(
                        UUID.randomUUID().toString(),
                        sessionId,
                        segmentId,
                        context.sourceText(),
                        context.sourceLanguage(),
                        context.targetLanguage()
                );

        try {
            // AI 번역의 총 6초 deadline 뒤 오류 응답까지 받을 수 있도록 BE는 전달 여유를 둔다.
            AiVoiceTranslationRetryResponse response = webClient.post()
                    .uri(
                            normalizeBaseUrl(aiServerUrl)
                                    + "/internal/v1/voice/translation/retry"
                    )
                    .header("X-API-KEY", aiServerApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, responseError -> responseError.bodyToMono(JsonNode.class)
                            .map(body -> retryFailed("AI translation retry failed.", aiErrorCode(body)))
                            .defaultIfEmpty(retryFailed("AI translation retry returned an empty error response.")))
                    .bodyToMono(AiVoiceTranslationRetryResponse.class)
                    .block(Duration.ofMillis(retryTimeoutMs));

            if (response == null) {
                throw retryFailed(
                        "AI translation retry returned an empty response."
                );
            }
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 외부 timeout/실패를 하나의 일반 오류로 숨기지 않아 사용자가 재시도 원인을 구분한다.
            Throwable cause = e;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            if (cause instanceof TimeoutException) {
                throw retryFailed("AI translation retry timed out.", VoiceErrorCode.AI_TRANSLATION_TIMEOUT);
            }
            throw retryFailed("AI translation retry failed.");
        }
    }

    private String aiErrorCode(JsonNode response) {
        // AI의 공개 코드만 전달하며 외부 오류 메시지나 원문은 노출하지 않는다.
        String code = response.path("detail").path("code").asText("");
        return code.matches("VOICE_AI_[A-Z_]{1,64}") ? code : VoiceErrorCode.TRANSLATION_RETRY_FAILED;
    }

    private String normalizeBaseUrl(String url) {
        return url.endsWith("/")
                ? url.substring(0, url.length() - 1)
                : url;
    }

    private BusinessException retryFailed(String message) {
        return retryFailed(message, VoiceErrorCode.TRANSLATION_RETRY_FAILED);
    }

    private BusinessException retryFailed(String message, String code) {
        return new BusinessException(message, code);
    }
}
