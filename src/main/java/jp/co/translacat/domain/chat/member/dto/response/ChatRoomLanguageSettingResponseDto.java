package jp.co.translacat.domain.chat.member.dto.response;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;

public record ChatRoomLanguageSettingResponseDto(
        Long chatRoomId,
        Long userId,
        String originalLanguageCode,
        String translationLanguageCode,
        boolean showOriginal,
        boolean showTranslation,
        boolean roomLanguageSettingApplied
) {

    public static ChatRoomLanguageSettingResponseDto from(
            ChatRoomMember chatRoomMember,
            ChatLanguageSettingResult languageSetting
    ) {
        return new ChatRoomLanguageSettingResponseDto(
                chatRoomMember.getChatRoom().getId(),
                chatRoomMember.getUser().getId(),
                languageSetting.originalLanguageCode(),
                languageSetting.translationLanguageCode(),
                chatRoomMember.isShowOriginal(),
                chatRoomMember.isShowTranslation(),
                languageSetting.roomLanguageSettingApplied()
        );
    }
}