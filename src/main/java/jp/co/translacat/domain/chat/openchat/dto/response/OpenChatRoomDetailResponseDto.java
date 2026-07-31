package jp.co.translacat.domain.chat.openchat.dto.response;

import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatJoinBlockedReason;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatRoomStatus;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatVisibility;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;

import java.time.LocalDateTime;

public record OpenChatRoomDetailResponseDto(
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
        ChatRoomMemberRole myRole,
        OpenChatMemberProfileResponseDto ownerProfile,
        OpenChatMemberProfileResponseDto myOpenProfile,
        LocalDateTime lastActivityAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
