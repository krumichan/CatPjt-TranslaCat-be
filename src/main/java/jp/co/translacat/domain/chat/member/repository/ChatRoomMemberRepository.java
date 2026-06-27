package jp.co.translacat.domain.chat.member.repository;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    Optional<ChatRoomMember> findByChatRoomAndUser(
            ChatRoom chatRoom,
            User user
    );

    Optional<ChatRoomMember> findByChatRoomIdAndUserId(
            Long chatRoomId,
            Long userId
    );

    Optional<ChatRoomMember> findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
            Long chatRoomId,
            Long userId
    );

    List<ChatRoomMember> findByChatRoomIdAndActiveTrueAndDeletedAtIsNull(
            Long chatRoomId
    );

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