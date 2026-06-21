package jp.co.translacat.domain.chat.room.dto.request;

import jp.co.translacat.domain.chat.room.enums.ChatRoomType;

import java.util.List;

public record ChatRoomCreateRequestDto(
        ChatRoomType roomType,
        String name,
        String description,
        List<Long> memberUserIds
) {
}