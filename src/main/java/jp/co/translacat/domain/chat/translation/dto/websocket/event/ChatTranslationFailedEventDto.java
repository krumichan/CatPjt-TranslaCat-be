package jp.co.translacat.domain.chat.translation.dto.websocket.event;

import jp.co.translacat.domain.chat.translation.enums.ChatMessageTranslationStatus;
import jp.co.translacat.domain.chat.translation.event.ChatMessageTranslationFailedEvent;
import jp.co.translacat.domain.chat.websocket.enums.ChatWebSocketEventType;

import java.time.LocalDateTime;

public record ChatTranslationFailedEventDto(
        String eventType,
        Long chatRoomId,
        Long messageId,
        Long translationId,
        String languageCode,
        String translatedContent,
        ChatMessageTranslationStatus status,
        String failureReason,
        LocalDateTime occurredAt
) {

    public static ChatTranslationFailedEventDto from(
            ChatMessageTranslationFailedEvent event
    ) {
        return new ChatTranslationFailedEventDto(
                ChatWebSocketEventType.TRANSLATION_FAILED.getEventName(),
                event.chatRoomId(),
                event.messageId(),
                event.translationId(),
                event.languageCode(),
                null,
                ChatMessageTranslationStatus.FAILED,
                event.failureReason(),
                LocalDateTime.now()
        );
    }
}