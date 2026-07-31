package jp.co.translacat.domain.chat.read.repository;

import java.util.List;
import java.util.Map;

public interface ChatUnreadCountRepository {

    Map<Long, Long> countUnreadByRoomIds(
            Long userId,
            List<Long> chatRoomIds
    );

    default long countUnread(Long userId, Long chatRoomId) {
        if (userId == null || chatRoomId == null) {
            return 0L;
        }
        return countUnreadByRoomIds(
                userId,
                List.of(chatRoomId)
        ).getOrDefault(chatRoomId, 0L);
    }
}
