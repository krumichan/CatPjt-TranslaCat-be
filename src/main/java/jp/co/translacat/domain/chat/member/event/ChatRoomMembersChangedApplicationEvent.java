package jp.co.translacat.domain.chat.member.event;

import java.time.LocalDateTime;

public record ChatRoomMembersChangedApplicationEvent(
        Long roomId,
        LocalDateTime occurredAt
) {
    public static ChatRoomMembersChangedApplicationEvent of(Long roomId) {
        return new ChatRoomMembersChangedApplicationEvent(
                roomId,
                LocalDateTime.now()
        );
    }
}
