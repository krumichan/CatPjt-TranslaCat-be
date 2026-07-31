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
        long unreadCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        DirectPartnerProfileResponseDto directPartner
) {
    /**
     * 기존 테스트/호출부 호환용 생성자.
     * FRIEND DIRECT partner 정보와 unreadCount가 필요 없는
     * 기존 호출은 각각 null, 0으로 응답한다.
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
                0L,
                createdAt,
                updatedAt,
                null
        );
    }

    /**
     * 기존 directPartner 포함 호출부 호환용 생성자.
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
            LocalDateTime updatedAt,
            DirectPartnerProfileResponseDto directPartner
    ) {
        this(
                id,
                roomType,
                sourceType,
                name,
                description,
                ownerId,
                memberCount,
                0L,
                createdAt,
                updatedAt,
                directPartner
        );
    }

    public static ChatRoomListItemResponseDto from(
            ChatRoom chatRoom,
            long memberCount
    ) {
        return from(chatRoom, memberCount, 0L, null);
    }

    public static ChatRoomListItemResponseDto from(
            ChatRoom chatRoom,
            long memberCount,
            DirectPartnerProfileResponseDto directPartner
    ) {
        return from(
                chatRoom,
                memberCount,
                0L,
                directPartner
        );
    }

    public static ChatRoomListItemResponseDto from(
            ChatRoom chatRoom,
            long memberCount,
            long unreadCount,
            DirectPartnerProfileResponseDto directPartner
    ) {
        return new ChatRoomListItemResponseDto(
                chatRoom.getId(),
                chatRoom.getRoomType(),
                chatRoom.getSourceType(),
                chatRoom.getName(),
                chatRoom.getDescription(),
                resolveOwnerId(chatRoom),
                memberCount,
                unreadCount,
                chatRoom.getCreatedAt(),
                chatRoom.getUpdatedAt(),
                directPartner
        );
    }
    private static Long resolveOwnerId(ChatRoom chatRoom) {
        if (chatRoom.getRoomType() == ChatRoomType.OPEN) {
            return null;
        }
        return chatRoom.getOwner() != null
                ? chatRoom.getOwner().getId()
                : null;
    }

}
