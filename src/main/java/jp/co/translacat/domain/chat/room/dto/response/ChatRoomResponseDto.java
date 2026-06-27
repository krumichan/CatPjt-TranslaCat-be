package jp.co.translacat.domain.chat.room.dto.response;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;

import java.time.LocalDateTime;

public record ChatRoomResponseDto(
        Long id,
        ChatRoomType roomType,
        ChatRoomSourceType sourceType,
        String name,
        String description,
        Long ownerId,
        boolean active,
        String originalLanguageCode,
        String translationLanguageCode,
        boolean roomLanguageSettingApplied,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ChatRoomResponseDto from(
            ChatRoom chatRoom,
            ChatLanguageSettingResult languageSetting
    ) {
        return new ChatRoomResponseDto(
                chatRoom.getId(),
                chatRoom.getRoomType(),
                chatRoom.getSourceType(),
                chatRoom.getName(),
                chatRoom.getDescription(),
                chatRoom.getOwner() != null ? chatRoom.getOwner().getId() : null,
                chatRoom.isActive(),
                languageSetting.originalLanguageCode(),
                languageSetting.translationLanguageCode(),
                languageSetting.roomLanguageSettingApplied(),
                chatRoom.getCreatedAt(),
                chatRoom.getUpdatedAt()
        );
    }
}