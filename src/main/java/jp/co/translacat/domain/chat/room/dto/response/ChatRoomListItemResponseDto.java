package jp.co.translacat.domain.chat.room.dto.response;

import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;

import java.time.LocalDateTime;

public record ChatRoomListItemResponseDto(
        Long id,
        ChatRoomType roomType,
        ChatRoomSourceType sourceType,
        String name,
        String description,
        Long ownerId,
        long memberCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ChatRoomListItemResponseDto from(
            ChatRoom chatRoom,
            long memberCount
    ) {
        return new ChatRoomListItemResponseDto(
                chatRoom.getId(),
                chatRoom.getRoomType(),
                chatRoom.getSourceType(),
                chatRoom.getName(),
                chatRoom.getDescription(),
                chatRoom.getOwner() != null ? chatRoom.getOwner().getId() : null,
                memberCount,
                chatRoom.getCreatedAt(),
                chatRoom.getUpdatedAt()
        );
    }
}