package jp.co.translacat.domain.chat.room.dto.request;

import java.util.List;

public record ChatRoomGroupConversionRequestDto(
        String name,
        String description,
        List<Long> targetUserIds,
        List<String> targetPublicIds
) {
}
