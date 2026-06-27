package jp.co.translacat.domain.chat.room.repository;

import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;

import java.util.Optional;

public interface ChatRoomRepositoryCustom {

    Optional<ChatRoom> findActiveDirectRoomByUserIds(
            Long userId1,
            Long userId2
    );

    Optional<ChatRoom> findActiveDirectRoomByUserIdsAndSourceType(
            Long userId1,
            Long userId2,
            ChatRoomSourceType sourceType
    );
}