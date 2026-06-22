package jp.co.translacat.infrastructure.chat.translation.client;

import jp.co.translacat.domain.chat.translation.port.ChatTranslationClient;
import jp.co.translacat.infrastructure.client.ai.server.AiServerClient;
import jp.co.translacat.infrastructure.client.ai.server.dto.AiChatTranslationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "translacat.ai.chat-translation.mode",
        havingValue = "real",
        matchIfMissing = true
)
public class AiServerChatTranslationClient implements ChatTranslationClient {

    private final AiServerClient aiServerClient;

    @Override
    public String translate(
            String originalText,
            String targetLanguageCode
    ) {
        AiChatTranslationResponse response =
                aiServerClient.callChatTranslation(
                        originalText,
                        targetLanguageCode
                );

        if (response == null
                || response.translatedText() == null
                || response.translatedText().isBlank()) {
            throw new IllegalStateException("AI translation response is empty.");
        }

        return response.translatedText().trim();
    }
}