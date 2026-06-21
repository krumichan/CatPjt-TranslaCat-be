package jp.co.translacat.domain.chat.room.dto.response;

import java.util.List;

public record ChatRoomListResponseDto(
        List<ChatRoomListItemResponseDto> chatRooms
) {

    public static ChatRoomListResponseDto from(List<ChatRoomListItemResponseDto> chatRooms) {
        return new ChatRoomListResponseDto(chatRooms);
    }
}