package jp.co.translacat.domain.chat.ai.repository;

import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiActivity;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatRoomAiActivityRepositoryCustom {

    Optional<ChatRoomAiActivity> findByIdForUpdate(Long activityId);

    Optional<ChatRoomAiActivity> findByChatRoomIdForUpdate(Long chatRoomId);

    List<Long> findDueActivityIds(
            LocalDateTime now,
            Collection<ChatRoomType> roomTypes,
            Pageable pageable
    );
}