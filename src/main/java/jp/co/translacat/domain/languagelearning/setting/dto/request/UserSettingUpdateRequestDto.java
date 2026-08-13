package jp.co.translacat.domain.languagelearning.setting.dto.request;

public record UserSettingUpdateRequestDto(
        String originLanguage,
        String learningLanguage,
        String timezone,
        Integer dailySentenceCount
) {
}
