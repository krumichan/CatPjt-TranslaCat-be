package jp.co.translacat.domain.chat.openchat.event;

import java.time.LocalDateTime;

public record OpenChatRoomClosedApplicationEvent(
        Long roomId,
        LocalDateTime closedAt,
        Long actorUserId,
        LocalDateTime occurredAt
) {

    public OpenChatRoomClosedApplicationEvent(
            Long roomId,
            LocalDateTime closedAt,
            LocalDateTime occurredAt
    ) {
        this(
                roomId,
                closedAt,
                null,
                occurredAt
        );
    }

    public static OpenChatRoomClosedApplicationEvent of(
            Long roomId,
            LocalDateTime closedAt
    ) {
        return of(roomId, closedAt, null);
    }

    public static OpenChatRoomClosedApplicationEvent of(
            Long roomId,
            LocalDateTime closedAt,
            Long actorUserId
    ) {
        return new OpenChatRoomClosedApplicationEvent(
                roomId,
                closedAt,
                actorUserId,
                LocalDateTime.now()
        );
    }
}
