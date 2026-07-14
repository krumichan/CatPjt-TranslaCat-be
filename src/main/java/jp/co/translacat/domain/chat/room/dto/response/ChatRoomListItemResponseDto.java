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
        LocalDateTime updatedAt,
        DirectPartnerProfileResponseDto directPartner
) {

    /**
     * 기존 테스트/호출부 호환용 생성자.
     * FRIEND DIRECT partner 정보가 필요 없는 기존 호출은 null로 응답한다.
     */
    public ChatRoomListItemResponseDto(
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
        this(
                id,
                roomType,
                sourceType,
                name,
                description,
                ownerId,
                memberCount,
                createdAt,
                updatedAt,
                null
        );
    }

    public static ChatRoomListItemResponseDto from(
            ChatRoom chatRoom,
            long memberCount
    ) {
        return from(chatRoom, memberCount, null);
    }

    public static ChatRoomListItemResponseDto from(
            ChatRoom chatRoom,
            long memberCount,
            DirectPartnerProfileResponseDto directPartner
    ) {
        return new ChatRoomListItemResponseDto(
                chatRoom.getId(),
                chatRoom.getRoomType(),
                chatRoom.getSourceType(),
                chatRoom.getName(),
                chatRoom.getDescription(),
                chatRoom.getOwner() != null
                        ? chatRoom.getOwner().getId()
                        : null,
                memberCount,
                chatRoom.getCreatedAt(),
                chatRoom.getUpdatedAt(),
                directPartner
        );
    }
}
