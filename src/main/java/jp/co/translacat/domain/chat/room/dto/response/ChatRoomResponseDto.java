package jp.co.translacat.domain.chat.room.dto.response;

import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;

import java.time.LocalDateTime;

public record ChatRoomResponseDto(
        Long id,
        ChatRoomType roomType,
        String name,
        String description,
        Long ownerId,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ChatRoomResponseDto from(ChatRoom chatRoom) {
        return new ChatRoomResponseDto(
                chatRoom.getId(),
                chatRoom.getRoomType(),
                chatRoom.getName(),
                chatRoom.getDescription(),
                chatRoom.getOwner() != null ? chatRoom.getOwner().getId() : null,
                chatRoom.isActive(),
                chatRoom.getCreatedAt(),
                chatRoom.getUpdatedAt()
        );
    }
}