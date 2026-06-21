package jp.co.translacat.domain.chat.room.repository;

import jp.co.translacat.domain.chat.room.entity.ChatRoom;

import java.util.Optional;

public interface ChatRoomRepositoryCustom {
    Optional<ChatRoom> findActiveDirectRoomByUserIds(
            Long userId1,
            Long userId2
    );
}
