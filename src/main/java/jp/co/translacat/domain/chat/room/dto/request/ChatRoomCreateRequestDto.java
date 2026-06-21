package jp.co.translacat.domain.chat.room.dto.request;

import jakarta.validation.constraints.NotNull;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;

import java.util.List;

public record ChatRoomCreateRequestDto(
        @NotNull(message = "채팅방 타입은 필수입니다.")
        ChatRoomType roomType,

        String name,

        String description,

        @NotNull(message = "채팅방 멤버 목록은 필수입니다.")
        List<Long> memberUserIds
) {
}