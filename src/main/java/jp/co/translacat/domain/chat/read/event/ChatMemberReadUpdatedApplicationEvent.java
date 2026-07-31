package jp.co.translacat.domain.chat.read.event;

import java.time.LocalDateTime;

public record ChatMemberReadUpdatedApplicationEvent(
        Long chatRoomId,
        Long readerUserId,
        Long previousLastReadMessageId,
        Long lastReadMessageId,
        LocalDateTime readAt
) {
    public static ChatMemberReadUpdatedApplicationEvent of(
            Long chatRoomId,
            Long readerUserId,
            Long previousLastReadMessageId,
            Long lastReadMessageId,
            LocalDateTime readAt
    ) {
        return new ChatMemberReadUpdatedApplicationEvent(
                chatRoomId,
                readerUserId,
                previousLastReadMessageId,
                lastReadMessageId,
                readAt
        );
    }
}
