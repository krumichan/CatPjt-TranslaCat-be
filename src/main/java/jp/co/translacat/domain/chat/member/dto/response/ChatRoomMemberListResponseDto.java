package jp.co.translacat.domain.chat.member.dto.response;

import java.util.List;

public record ChatRoomMemberListResponseDto(
        List<ChatRoomMemberResponseDto> members
) {

    public static ChatRoomMemberListResponseDto from(List<ChatRoomMemberResponseDto> members) {
        return new ChatRoomMemberListResponseDto(members);
    }
}