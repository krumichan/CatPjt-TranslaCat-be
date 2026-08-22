package jp.co.translacat.infrastructure.client.ai.server.voice;

import jp.co.translacat.domain.voice.model.VoiceTranslationRetryContext;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.client.ai.server.voice.dto.AiVoiceTranslationRetryRequest;
import jp.co.translacat.infrastructure.client.ai.server.voice.dto.AiVoiceTranslationRetryResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VoiceAiTranslationRetryClient {

    private final WebClient webClient;

    @Value("${ai-server.url}")
    private String aiServerUrl;

    @Value("${ai-server.api-key}")
    private String aiServerApiKey;

    @Value("${translacat.voice.ai-retry-timeout-ms:5000}")
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
            AiVoiceTranslationRetryResponse response = webClient.post()
                    .uri(
                            normalizeBaseUrl(aiServerUrl)
                                    + "/internal/v1/voice/translation/retry"
                    )
                    .header("X-API-KEY", aiServerApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
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
            throw retryFailed("AI translation retry failed.");
        }
    }

    private String normalizeBaseUrl(String url) {
        return url.endsWith("/")
                ? url.substring(0, url.length() - 1)
                : url;
    }

    private BusinessException retryFailed(String message) {
        return new BusinessException(
                message,
                VoiceErrorCode.TRANSLATION_RETRY_FAILED
        );
    }
}
