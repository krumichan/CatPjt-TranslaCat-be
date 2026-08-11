package jp.co.translacat.domain.chat.message.repository;

import jp.co.translacat.domain.chat.message.repository.projection.ChatMessageAnchorWindowQueryResult;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepositoryCustom {

    ChatMessageAnchorWindowQueryResult findAnchorWindowIds(
            Long chatRoomId,
            Long anchorMessageId,
            LocalDateTime joinedAt,
            int beforeSize,
            int afterSize
    );

    List<Long> findNextMessageIds(
            Long chatRoomId,
            Long cursorMessageId,
            LocalDateTime joinedAt,
            int limit
    );
}
