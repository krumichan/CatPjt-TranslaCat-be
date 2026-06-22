package jp.co.translacat.domain.chat.translation.event;

import jp.co.translacat.domain.chat.translation.entity.ChatMessageTranslation;

public record ChatMessageTranslationCompletedEvent(
        Long chatRoomId,
        Long messageId,
        Long translationId,
        String languageCode,
        String translatedContent
) {

    public static ChatMessageTranslationCompletedEvent from(
            ChatMessageTranslation translation
    ) {
        return new ChatMessageTranslationCompletedEvent(
                translation.getChatMessage().getChatRoom().getId(),
                translation.getChatMessage().getId(),
                translation.getId(),
                translation.getLanguageCode(),
                translation.getTranslatedContent()
        );
    }
}