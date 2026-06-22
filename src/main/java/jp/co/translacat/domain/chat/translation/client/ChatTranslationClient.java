package jp.co.translacat.domain.chat.translation.client;

public interface ChatTranslationClient {

    String translate(
            String originalText,
            String targetLanguageCode
    );
}