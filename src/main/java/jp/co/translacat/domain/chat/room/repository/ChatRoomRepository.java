package jp.co.translacat.domain.chat.room.repository;

import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends
        JpaRepository<ChatRoom, Long>,
        ChatRoomRepositoryCustom {

    Optional<ChatRoom> findByIdAndActiveTrueAndDeletedAtIsNull(Long id);

    List<ChatRoom> findByOwnerAndActiveTrueAndDeletedAtIsNull(User owner);

    List<ChatRoom> findByOwnerAndRoomTypeAndActiveTrueAndDeletedAtIsNull(
            User owner,
            ChatRoomType roomType
    );
}