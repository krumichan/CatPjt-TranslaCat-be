package jp.co.translacat.domain.chat.openchat.dto.response;

import java.util.List;

public record OpenChatRoomListResponseDto(
        List<OpenChatRoomListItemResponseDto> openChatRooms,
        Long nextCursorId,
        boolean hasNext
) {

    public static OpenChatRoomListResponseDto of(
            List<OpenChatRoomListItemResponseDto> openChatRooms,
            Long nextCursorId,
            boolean hasNext
    ) {
        return new OpenChatRoomListResponseDto(
                openChatRooms,
                nextCursorId,
                hasNext
        );
    }
}
