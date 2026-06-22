package jp.co.translacat.domain.chat.websocket.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatWebSocketEventType {

    MESSAGE_CREATED("chat.message.created"),
    TRANSLATION_COMPLETED("chat.translation.completed"),
    ERROR("chat.error");

    private final String eventName;
}