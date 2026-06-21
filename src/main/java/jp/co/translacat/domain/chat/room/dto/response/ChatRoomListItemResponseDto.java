package jp.co.translacat.domain.chat.room.dto.response;

import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;

import java.time.LocalDateTime;

public record ChatRoomListItemResponseDto(
        Long id,
        ChatRoomType roomType,
        String name,
        String description,
        Long ownerId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ChatRoomListItemResponseDto from(ChatRoom chatRoom) {
        return new ChatRoomListItemResponseDto(
                chatRoom.getId(),
                chatRoom.getRoomType(),
                chatRoom.getName(),
                chatRoom.getDescription(),
                chatRoom.getOwner() != null ? chatRoom.getOwner().getId() : null,
                chatRoom.getCreatedAt(),
                chatRoom.getUpdatedAt()
        );
    }
}