package jp.co.translacat.domain.chat.language.dto;

public record ChatLanguageSettingUpdateRequestDto(
        String originalLanguageCode,
        String translationLanguageCode,
        Boolean showOriginal,
        Boolean showTranslation
) {
}
