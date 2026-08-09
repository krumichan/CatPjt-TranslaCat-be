package jp.co.translacat.domain.chat.room.repository;

import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.user.entity.User;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository
        extends JpaRepository<ChatRoom, Long>,
        ChatRoomRepositoryCustom {

    Optional<ChatRoom> findByIdAndActiveTrueAndDeletedAtIsNull(
            Long id
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select room
            from ChatRoom room
            where room.id = :id
              and room.active = true
              and room.deletedAt is null
            """)
    Optional<ChatRoom> findActiveByIdForUpdate(@Param("id") Long id);

    List<ChatRoom> findByOwnerAndActiveTrueAndDeletedAtIsNull(
            User owner
    );

    List<ChatRoom>
    findByOwnerAndRoomTypeAndActiveTrueAndDeletedAtIsNull(
            User owner,
            ChatRoomType roomType
    );
}
