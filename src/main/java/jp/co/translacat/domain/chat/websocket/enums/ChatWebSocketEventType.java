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
    MEMBERS_CHANGED("chat.members.changed"),
    OPEN_PROFILE_UPDATED("chat.open-profile.updated"),
    MEMBER_ROLE_UPDATED("chat.member.role.updated"),
    MEMBER_BANNED("chat.member.banned"),
    ROOM_CLOSED("chat.room.closed"),
    PRESENCE_CHANGED("chat.presence.changed"),
    ERROR("chat.error");

    private final String eventName;
}
