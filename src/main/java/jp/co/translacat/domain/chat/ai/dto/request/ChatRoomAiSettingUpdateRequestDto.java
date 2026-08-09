package jp.co.translacat.domain.chat.ai.dto.request;

import jp.co.translacat.domain.chat.ai.enums.ChatAiDisclosureType;
import jp.co.translacat.domain.chat.ai.enums.ChatAiMentionPermission;

public record ChatRoomAiSettingUpdateRequestDto(
        ChatAiDisclosureType disclosureType,
        ChatAiMentionPermission mentionPermission,
        Boolean conversationEnabled,
        Boolean revivalEnabled
) {
}
