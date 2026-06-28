package jp.co.translacat.infrastructure.chat.translation.client;

import jp.co.translacat.domain.chat.translation.port.ChatTranslationClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        name = "translacat.ai.chat-translation.mode",
        havingValue = "temporary"
)
public class TemporaryChatTranslationClient implements ChatTranslationClient {

    @Override
    public String translate(
            String originalText,
            String targetLanguageCode
    ) {
        log.debug(
                "Temporary chat translation client called. targetLanguageCode={}",
                targetLanguageCode
        );

        return "[translated:" + targetLanguageCode + "] " + originalText;
    }
}