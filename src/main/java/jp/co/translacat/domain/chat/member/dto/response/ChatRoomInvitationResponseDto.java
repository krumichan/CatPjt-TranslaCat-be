package jp.co.translacat.domain.chat.member.dto.response;

import java.util.List;

public record ChatRoomInvitationResponseDto(
        Long roomId,
        boolean createdNewGroupRoom,
        List<ChatRoomInvitedMemberResponseDto> invitedMembers
) {

    public static ChatRoomInvitationResponseDto forExistingGroup(
            Long roomId,
            List<ChatRoomInvitedMemberResponseDto> invitedMembers
    ) {
        return new ChatRoomInvitationResponseDto(
                roomId,
                false,
                invitedMembers
        );
    }

    public static ChatRoomInvitationResponseDto forNewGroup(
            Long roomId,
            List<ChatRoomInvitedMemberResponseDto> invitedMembers
    ) {
        return new ChatRoomInvitationResponseDto(
                roomId,
                true,
                invitedMembers
        );
    }
}
