package jp.co.translacat.domain.chat.language.service;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.language.enums.ChatLanguageSettingSource;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatLanguageSettingResolver {

    private final UserChatLanguageSettingService userChatLanguageSettingService;

    public ChatLanguageSettingResult resolve(ChatRoomMember chatRoomMember) {
        ChatLanguageSettingResult defaultResult = userChatLanguageSettingService
                .resolveDefault(chatRoomMember.getUser().getId());

        if (!chatRoomMember.hasRoomLanguageSetting()) {
            return defaultResult;
        }

        String originalLanguageCode = ChatLanguageSettingSupport.normalizeOrDefault(
                chatRoomMember.getOriginalLanguageCode(),
                defaultResult.originalLanguageCode()
        );
        String translationLanguageCode = ChatLanguageSettingSupport.normalizeOrDefault(
                chatRoomMember.getTranslationLanguageCode(),
                defaultResult.translationLanguageCode()
        );

        return new ChatLanguageSettingResult(
                originalLanguageCode,
                translationLanguageCode,
                chatRoomMember.isShowOriginal(),
                chatRoomMember.isShowTranslation(),
                true,
                ChatLanguageSettingSource.ROOM_OVERRIDE
        );
    }
}
