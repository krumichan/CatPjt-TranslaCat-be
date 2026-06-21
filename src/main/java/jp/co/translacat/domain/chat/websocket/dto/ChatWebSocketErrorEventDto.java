package jp.co.translacat.domain.chat.websocket.dto;

import jp.co.translacat.domain.chat.websocket.enums.ChatWebSocketEventType;

import java.time.LocalDateTime;

public record ChatWebSocketErrorEventDto(
        String eventType,
        String errorCode,
        String message,
        LocalDateTime occurredAt
) {

    public static ChatWebSocketErrorEventDto business(String message) {
        return new ChatWebSocketErrorEventDto(
                ChatWebSocketEventType.ERROR.getEventName(),
                "CHAT_WEBSOCKET_BUSINESS_ERROR",
                message,
                LocalDateTime.now()
        );
    }

    public static ChatWebSocketErrorEventDto unauthorized(String message) {
        return new ChatWebSocketErrorEventDto(
                ChatWebSocketEventType.ERROR.getEventName(),
                "CHAT_WEBSOCKET_UNAUTHORIZED",
                message,
                LocalDateTime.now()
        );
    }

    public static ChatWebSocketErrorEventDto internal() {
        return new ChatWebSocketErrorEventDto(
                ChatWebSocketEventType.ERROR.getEventName(),
                "CHAT_WEBSOCKET_INTERNAL_ERROR",
                "WebSocket 메시지 처리 중 오류가 발생했습니다.",
                LocalDateTime.now()
        );
    }
}