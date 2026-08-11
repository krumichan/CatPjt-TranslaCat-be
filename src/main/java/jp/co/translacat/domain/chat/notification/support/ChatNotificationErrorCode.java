package jp.co.translacat.domain.chat.notification.support;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ChatNotificationErrorCode {

    public static final String NOT_FOUND =
            "CHAT_NOTIFICATION_NOT_FOUND";
    public static final String CURSOR_INVALID =
            "CHAT_NOTIFICATION_CURSOR_INVALID";
    public static final String PAGE_SIZE_INVALID =
            "CHAT_NOTIFICATION_PAGE_SIZE_INVALID";
}
