package jp.co.translacat.domain.chat.websocket.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatWebSocketEventType {

    MESSAGE_CREATED("chat.message.created"),
    TRANSLATION_COMPLETED("chat.translation.completed"),
    TRANSLATION_FAILED("chat.translation.failed"),
    READ_UPDATED("chat.read.updated"),
    MEMBER_READ_UPDATED("chat.member.read.updated"),
    ERROR("chat.error");

    private final String eventName;
}
