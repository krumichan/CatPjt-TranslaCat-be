package jp.co.translacat.domain.chat.language.dto;

import jp.co.translacat.domain.chat.language.enums.ChatLanguageSettingSource;

public record ChatLanguageSettingResult(
        String originalLanguageCode,
        String translationLanguageCode,
        boolean showOriginal,
        boolean showTranslation,
        boolean roomLanguageSettingApplied,
        ChatLanguageSettingSource source
) {

    public ChatLanguageSettingResult(
            String originalLanguageCode,
            String translationLanguageCode,
            boolean roomLanguageSettingApplied
    ) {
        this(
                originalLanguageCode,
                translationLanguageCode,
                true,
                true,
                roomLanguageSettingApplied,
                roomLanguageSettingApplied
                        ? ChatLanguageSettingSource.ROOM_OVERRIDE
                        : ChatLanguageSettingSource.SYSTEM
        );
    }
}
