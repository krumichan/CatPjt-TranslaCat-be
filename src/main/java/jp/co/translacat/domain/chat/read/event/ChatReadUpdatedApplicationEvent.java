package jp.co.translacat.domain.chat.read.event;

import jp.co.translacat.domain.chat.read.dto.response.ChatRoomReadResponseDto;

public record ChatReadUpdatedApplicationEvent(
        String destinationUsername,
        Long userId,
        ChatRoomReadResponseDto response
) {
    public static ChatReadUpdatedApplicationEvent of(
            String destinationUsername,
            Long userId,
            ChatRoomReadResponseDto response
    ) {
        return new ChatReadUpdatedApplicationEvent(
                destinationUsername,
                userId,
                response
        );
    }
}
