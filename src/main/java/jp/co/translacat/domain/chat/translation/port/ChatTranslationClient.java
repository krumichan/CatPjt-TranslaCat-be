package jp.co.translacat.domain.chat.translation.port;

public interface ChatTranslationClient {

    String translate(
            String originalText,
            String targetLanguageCode
    );
}