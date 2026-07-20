package jp.co.translacat.domain.chat.room.dto.response;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
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
        long memberCount,
        boolean active,
        String originalLanguageCode,
        String translationLanguageCode,
        boolean roomLanguageSettingApplied,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        ChatRoomMemberRole myRole,
        DirectPartnerProfileResponseDto directPartner
) {

    /**
     * 기존 테스트/호출부 호환용 생성자.
     */
    public ChatRoomResponseDto(
            Long id,
            ChatRoomType roomType,
            ChatRoomSourceType sourceType,
            String name,
            String description,
            Long ownerId,
            long memberCount,
            boolean active,
            String originalLanguageCode,
            String translationLanguageCode,
            boolean roomLanguageSettingApplied,
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
                active,
                originalLanguageCode,
                translationLanguageCode,
                roomLanguageSettingApplied,
                createdAt,
                updatedAt,
                null,
                null
        );
    }

    /**
     * BE #39 directPartner 추가 이후 호출부 호환용 생성자.
     */
    public ChatRoomResponseDto(
            Long id,
            ChatRoomType roomType,
            ChatRoomSourceType sourceType,
            String name,
            String description,
            Long ownerId,
            long memberCount,
            boolean active,
            String originalLanguageCode,
            String translationLanguageCode,
            boolean roomLanguageSettingApplied,
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
                active,
                originalLanguageCode,
                translationLanguageCode,
                roomLanguageSettingApplied,
                createdAt,
                updatedAt,
                null,
                directPartner
        );
    }

    public static ChatRoomResponseDto from(
            ChatRoom chatRoom,
            ChatLanguageSettingResult languageSetting,
            long memberCount
    ) {
        return from(
                chatRoom,
                languageSetting,
                memberCount,
                null,
                null
        );
    }

    public static ChatRoomResponseDto from(
            ChatRoom chatRoom,
            ChatLanguageSettingResult languageSetting,
            long memberCount,
            DirectPartnerProfileResponseDto directPartner
    ) {
        return from(
                chatRoom,
                languageSetting,
                memberCount,
                null,
                directPartner
        );
    }

    public static ChatRoomResponseDto from(
            ChatRoom chatRoom,
            ChatLanguageSettingResult languageSetting,
            long memberCount,
            ChatRoomMemberRole myRole,
            DirectPartnerProfileResponseDto directPartner
    ) {
        return new ChatRoomResponseDto(
                chatRoom.getId(),
                chatRoom.getRoomType(),
                chatRoom.getSourceType(),
                chatRoom.getName(),
                chatRoom.getDescription(),
                chatRoom.getOwner() != null
                        ? chatRoom.getOwner().getId()
                        : null,
                memberCount,
                chatRoom.isActive(),
                languageSetting.originalLanguageCode(),
                languageSetting.translationLanguageCode(),
                languageSetting.roomLanguageSettingApplied(),
                chatRoom.getCreatedAt(),
                chatRoom.getUpdatedAt(),
                myRole,
                directPartner
        );
    }
}
