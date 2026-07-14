package jp.co.translacat.domain.chat.member.repository;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository
        extends JpaRepository<ChatRoomMember, Long> {

    Optional<ChatRoomMember> findByChatRoomAndUser(
            ChatRoom chatRoom,
            User user
    );

    Optional<ChatRoomMember> findByChatRoomIdAndUserId(
            Long chatRoomId,
            Long userId
    );

    @EntityGraph(attributePaths = {"chatRoom", "user"})
    Optional<ChatRoomMember>
    findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
            Long chatRoomId,
            Long userId
    );

    @EntityGraph(attributePaths = {"chatRoom", "user"})
    List<ChatRoomMember> findByChatRoomIdAndActiveTrueAndDeletedAtIsNull(
            Long chatRoomId
    );

    @EntityGraph(attributePaths = {"chatRoom", "user"})
    List<ChatRoomMember> findByChatRoomIdInAndActiveTrueAndDeletedAtIsNull(
            Collection<Long> chatRoomIds
    );

    @EntityGraph(attributePaths = {"chatRoom", "user"})
    List<ChatRoomMember> findByUserIdAndActiveTrueAndDeletedAtIsNull(
            Long userId
    );

    boolean existsByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
            Long chatRoomId,
            Long userId
    );

    long countByChatRoomIdAndActiveTrueAndDeletedAtIsNull(
            Long chatRoomId
    );
}
