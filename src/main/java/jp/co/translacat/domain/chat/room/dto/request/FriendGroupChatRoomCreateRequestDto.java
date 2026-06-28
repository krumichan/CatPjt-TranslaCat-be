package jp.co.translacat.domain.chat.room.dto.request;

import java.util.List;

public record FriendGroupChatRoomCreateRequestDto(
        String name,
        String description,
        List<Long> memberUserIds
) {
}
