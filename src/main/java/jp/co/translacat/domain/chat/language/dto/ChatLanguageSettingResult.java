package jp.co.translacat.domain.chat.language.dto;

public record ChatLanguageSettingResult(
        String originalLanguageCode,
        String translationLanguageCode,
        boolean roomLanguageSettingApplied
) {
}