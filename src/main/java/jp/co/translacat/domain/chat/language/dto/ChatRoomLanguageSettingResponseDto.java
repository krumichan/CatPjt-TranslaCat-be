package jp.co.translacat.domain.chat.language.dto;

import jp.co.translacat.domain.chat.language.enums.ChatLanguageSettingSource;

public record ChatRoomLanguageSettingResponseDto(
        Long chatRoomId,
        Long userId,
        String originalLanguageCode,
        String translationLanguageCode,
        boolean showOriginal,
        boolean showTranslation,
        boolean roomLanguageSettingApplied,
        ChatLanguageSettingSource source
) {

    public static ChatRoomLanguageSettingResponseDto from(
            Long chatRoomId,
            Long userId,
            ChatLanguageSettingResult result
    ) {
        return new ChatRoomLanguageSettingResponseDto(
                chatRoomId,
                userId,
                result.originalLanguageCode(),
                result.translationLanguageCode(),
                result.showOriginal(),
                result.showTranslation(),
                result.roomLanguageSettingApplied(),
                result.source()
        );
    }
}
