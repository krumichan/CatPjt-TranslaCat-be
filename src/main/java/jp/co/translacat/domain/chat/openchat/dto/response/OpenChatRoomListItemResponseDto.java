package jp.co.translacat.domain.chat.openchat.dto.response;

import jp.co.translacat.domain.chat.openchat.enums.OpenChatJoinBlockedReason;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatRoomStatus;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatVisibility;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;

import java.time.LocalDateTime;

public record OpenChatRoomListItemResponseDto(
        Long id,
        ChatRoomType roomType,
        ChatRoomSourceType sourceType,
        String name,
        String description,
        OpenChatVisibility visibility,
        OpenChatRoomStatus status,
        long memberCount,
        int maxMemberCount,
        boolean joined,
        boolean joinable,
        OpenChatJoinBlockedReason joinBlockedReason,
        LocalDateTime lastActivityAt,
        OpenChatMemberProfileResponseDto ownerProfile
) {
}
