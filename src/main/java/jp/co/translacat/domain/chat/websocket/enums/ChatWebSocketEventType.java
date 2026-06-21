package jp.co.translacat.domain.chat.websocket.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatWebSocketEventType {

    MESSAGE_CREATED("chat.message.created"),
    ERROR("chat.error");

    private final String eventName;
}