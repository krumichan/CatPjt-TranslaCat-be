package jp.co.translacat.domain.voice.model;

public record VoiceTranslationRetryContext(
        String sourceText,
        String sourceLanguage,
        String targetLanguage
) {
}
