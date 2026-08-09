package jp.co.translacat.domain.chat.openchat.dto.response;

import jp.co.translacat.domain.chat.ai.dto.response.ChatAiDisplayMemberResponseDto;
import jp.co.translacat.domain.chat.ai.enums.ChatAiDisclosureType;

import java.util.List;

public record OpenChatMemberListResponseDto(
        List<OpenChatMemberProfileResponseDto> members,
        List<ChatAiDisplayMemberResponseDto> aiMembers,
        ChatAiDisclosureType aiDisclosureType
) {

    public OpenChatMemberListResponseDto(
            List<OpenChatMemberProfileResponseDto> members
    ) {
        this(members, List.of(), null);
    }

    public static OpenChatMemberListResponseDto of(
            List<OpenChatMemberProfileResponseDto> members
    ) {
        return new OpenChatMemberListResponseDto(List.copyOf(members));
    }

    public static OpenChatMemberListResponseDto of(
            List<OpenChatMemberProfileResponseDto> members,
            List<ChatAiDisplayMemberResponseDto> aiMembers,
            ChatAiDisclosureType aiDisclosureType
    ) {
        return new OpenChatMemberListResponseDto(
                List.copyOf(members),
                List.copyOf(aiMembers),
                aiDisclosureType
        );
    }
}
