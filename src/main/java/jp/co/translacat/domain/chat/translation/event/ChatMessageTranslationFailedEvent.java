package jp.co.translacat.domain.chat.translation.event;

import jp.co.translacat.domain.chat.translation.entity.ChatMessageTranslation;

public record ChatMessageTranslationFailedEvent(
        Long chatRoomId,
        Long messageId,
        Long translationId,
        String languageCode,
        String failureReason
) {

    public static ChatMessageTranslationFailedEvent from(
            ChatMessageTranslation translation
    ) {
        return new ChatMessageTranslationFailedEvent(
                translation.getChatMessage().getChatRoom().getId(),
                translation.getChatMessage().getId(),
                translation.getId(),
                translation.getLanguageCode(),
                translation.getFailureReason()
        );
    }
}