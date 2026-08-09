package jp.co.translacat.domain.chat.ai.dto.response;

import jp.co.translacat.domain.chat.ai.enums.ChatAiDisclosureType;

public record ChatAiRoomSummaryResponseDto(
        boolean aiEnabled,
        int aiMemberCount,
        ChatAiDisclosureType disclosureType
) {
    public static ChatAiRoomSummaryResponseDto disabled() {
        return new ChatAiRoomSummaryResponseDto(
                false,
                0,
                null
        );
    }
}
