package jp.co.translacat.domain.chat.read.dto.response;

import jp.co.translacat.domain.chat.common.json.ChatUtcTimestamp;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;

import java.time.LocalDateTime;

public record ChatRoomReadResponseDto(
        Long chatRoomId,
        Long lastReadMessageId,
        @ChatUtcTimestamp LocalDateTime lastReadAt,
        long unreadCount
) {
    public static ChatRoomReadResponseDto from(
            ChatRoomMember member,
            long unreadCount
    ) {
        return new ChatRoomReadResponseDto(
                member.getChatRoom().getId(),
                member.getLastReadMessageId(),
                member.getLastReadAt(),
                unreadCount
        );
    }
}
