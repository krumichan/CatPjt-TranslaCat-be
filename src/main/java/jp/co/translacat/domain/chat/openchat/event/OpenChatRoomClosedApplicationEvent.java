package jp.co.translacat.domain.chat.openchat.event;

import java.time.LocalDateTime;

public record OpenChatRoomClosedApplicationEvent(
        Long roomId,
        LocalDateTime closedAt,
        LocalDateTime occurredAt
) {

    public static OpenChatRoomClosedApplicationEvent of(
            Long roomId,
            LocalDateTime closedAt
    ) {
        return new OpenChatRoomClosedApplicationEvent(
                roomId,
                closedAt,
                LocalDateTime.now()
        );
    }
}
