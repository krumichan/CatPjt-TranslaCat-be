package jp.co.translacat.domain.chat.read.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ChatMessageUnreadMemberCountRepository {

    Map<Long, Long> countUnreadMembersByMessageIds(
            Collection<Long> messageIds
    );

    default long countUnreadMembers(Long messageId) {
        if (messageId == null) {
            return 0L;
        }
        return countUnreadMembersByMessageIds(
                List.of(messageId)
        ).getOrDefault(messageId, 0L);
    }
}
