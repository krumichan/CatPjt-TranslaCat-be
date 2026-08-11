package jp.co.translacat.domain.chat.notification.repository;

import jp.co.translacat.domain.chat.notification.repository.projection.ChatNotificationRoomQueryRow;
import jp.co.translacat.domain.chat.notification.repository.projection.ChatNotificationUnreadSummary;

import java.util.List;

public interface ChatNotificationChatQueryRepository {

    List<ChatNotificationRoomQueryRow> findUnreadChatRoomPage(
            Long userId,
            Long cursorMessageId,
            int limit
    );

    ChatNotificationUnreadSummary summarizeUnreadChats(Long userId);
}
