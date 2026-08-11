package jp.co.translacat.domain.chat.read.event;

import java.time.LocalDateTime;

public record ChatMemberReadUpdatedApplicationEvent(
        Long chatRoomId,
        Long readerUserId,
        Long readerOpenChatMemberId,
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
        return of(
                chatRoomId,
                readerUserId,
                null,
                previousLastReadMessageId,
                lastReadMessageId,
                readAt
        );
    }

    public static ChatMemberReadUpdatedApplicationEvent of(
            Long chatRoomId,
            Long readerUserId,
            Long readerOpenChatMemberId,
            Long previousLastReadMessageId,
            Long lastReadMessageId,
            LocalDateTime readAt
    ) {
        return new ChatMemberReadUpdatedApplicationEvent(
                chatRoomId,
                readerUserId,
                readerOpenChatMemberId,
                previousLastReadMessageId,
                lastReadMessageId,
                readAt
        );
    }
}
