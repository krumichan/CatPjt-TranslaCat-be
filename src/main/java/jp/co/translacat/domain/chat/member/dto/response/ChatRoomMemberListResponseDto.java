package jp.co.translacat.domain.chat.member.dto.response;

import jp.co.translacat.domain.chat.ai.dto.response.ChatAiDisplayMemberResponseDto;
import jp.co.translacat.domain.chat.ai.enums.ChatAiDisclosureType;

import java.util.List;

public record ChatRoomMemberListResponseDto(
        List<ChatRoomMemberResponseDto> members,
        List<ChatAiDisplayMemberResponseDto> aiMembers,
        ChatAiDisclosureType aiDisclosureType
) {

    public ChatRoomMemberListResponseDto(
            List<ChatRoomMemberResponseDto> members
    ) {
        this(members, List.of(), null);
    }

    public static ChatRoomMemberListResponseDto from(
            List<ChatRoomMemberResponseDto> members
    ) {
        return new ChatRoomMemberListResponseDto(members);
    }

    public static ChatRoomMemberListResponseDto of(
            List<ChatRoomMemberResponseDto> members,
            List<ChatAiDisplayMemberResponseDto> aiMembers,
            ChatAiDisclosureType aiDisclosureType
    ) {
        return new ChatRoomMemberListResponseDto(
                List.copyOf(members),
                List.copyOf(aiMembers),
                aiDisclosureType
        );
    }
}
