package jp.co.translacat.domain.chat.language.dto;

import jp.co.translacat.domain.chat.language.entity.UserChatLanguageSetting;
import jp.co.translacat.domain.chat.language.enums.ChatLanguageSettingSource;

public record UserChatLanguageSettingResponseDto(
        Long userId,
        String originalLanguageCode,
        String translationLanguageCode,
        boolean showOriginal,
        boolean showTranslation,
        ChatLanguageSettingSource source
) {

    public static UserChatLanguageSettingResponseDto from(
            UserChatLanguageSetting setting
    ) {
        return new UserChatLanguageSettingResponseDto(
                setting.getUser().getId(),
                setting.getOriginalLanguageCode(),
                setting.getTranslationLanguageCode(),
                setting.isShowOriginal(),
                setting.isShowTranslation(),
                ChatLanguageSettingSource.DEFAULT
        );
    }

    public static UserChatLanguageSettingResponseDto fromResult(
            Long userId,
            ChatLanguageSettingResult result
    ) {
        return new UserChatLanguageSettingResponseDto(
                userId,
                result.originalLanguageCode(),
                result.translationLanguageCode(),
                result.showOriginal(),
                result.showTranslation(),
                result.source()
        );
    }
}
