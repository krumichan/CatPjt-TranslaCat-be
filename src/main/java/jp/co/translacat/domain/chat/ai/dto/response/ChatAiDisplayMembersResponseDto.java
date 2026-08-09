package jp.co.translacat.domain.chat.ai.dto.response;

import jp.co.translacat.domain.chat.ai.enums.ChatAiDisclosureType;

import java.util.List;

public record ChatAiDisplayMembersResponseDto(
        ChatAiDisclosureType disclosureType,
        List<ChatAiDisplayMemberResponseDto> members
) {
    public static ChatAiDisplayMembersResponseDto empty() {
        return new ChatAiDisplayMembersResponseDto(null, List.of());
    }
}
