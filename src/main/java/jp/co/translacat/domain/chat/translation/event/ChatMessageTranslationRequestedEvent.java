package jp.co.translacat.domain.chat.translation.event;

import java.util.List;

public record ChatMessageTranslationRequestedEvent(
        Long chatRoomId,
        Long messageId,
        Long senderUserId,
        List<Long> translationIds
) {

    public static ChatMessageTranslationRequestedEvent of(
            Long chatRoomId,
            Long messageId,
            Long senderUserId,
            List<Long> translationIds
    ) {
        return new ChatMessageTranslationRequestedEvent(
                chatRoomId,
                messageId,
                senderUserId,
                List.copyOf(translationIds)
        );
    }
}