package jp.co.translacat.domain.chat.ai.dto.response;

import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiSetting;
import jp.co.translacat.domain.chat.ai.enums.ChatAiDisclosureType;
import jp.co.translacat.domain.chat.ai.enums.ChatAiMentionPermission;

public record ChatRoomAiSettingResponseDto(
        Long chatRoomId,
        boolean aiEnabled,
        int currentAiMemberCount,
        int maxAiMembersPerRoom,
        ChatAiDisclosureType disclosureType,
        ChatAiMentionPermission mentionPermission,
        boolean conversationEnabled,
        boolean revivalEnabled
) {
    public static ChatRoomAiSettingResponseDto from(
            ChatRoomAiSetting setting,
            long currentAiMemberCount,
            int maxAiMembersPerRoom
    ) {
        return new ChatRoomAiSettingResponseDto(
                setting.getChatRoom().getId(),
                currentAiMemberCount > 0,
                Math.toIntExact(currentAiMemberCount),
                maxAiMembersPerRoom,
                setting.getDisclosureType(),
                setting.getMentionPermission(),
                setting.isConversationEnabled(),
                setting.isRevivalEnabled()
        );
    }
}
