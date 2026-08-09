package jp.co.translacat.domain.chat.ai.dto.response;

import java.util.List;

public record ChatAiMemberListResponseDto(
        Long chatRoomId,
        int currentCount,
        int maxCount,
        List<ChatAiMemberResponseDto> members
) {
}
