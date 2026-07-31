package jp.co.translacat.domain.chat.openchat.profile.repository;

import jp.co.translacat.domain.chat.openchat.profile.entity.OpenChatMemberProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OpenChatMemberProfileRepository
        extends JpaRepository<OpenChatMemberProfile, Long> {

    boolean existsByMemberCode(String memberCode);

    @EntityGraph(attributePaths = {
            "chatRoomMember",
            "chatRoomMember.chatRoom",
            "chatRoomMember.user"
    })
    Optional<OpenChatMemberProfile> findByChatRoomMemberId(
            Long chatRoomMemberId
    );

    @EntityGraph(attributePaths = {
            "chatRoomMember",
            "chatRoomMember.chatRoom",
            "chatRoomMember.user"
    })
    Optional<OpenChatMemberProfile>
    findByChatRoomMemberChatRoomIdAndChatRoomMemberUserId(
            Long chatRoomId,
            Long userId
    );

    @EntityGraph(attributePaths = {
            "chatRoomMember",
            "chatRoomMember.chatRoom",
            "chatRoomMember.user"
    })
    Optional<OpenChatMemberProfile>
    findByChatRoomMemberIdAndChatRoomMemberChatRoomId(
            Long openChatMemberId,
            Long chatRoomId
    );

    @EntityGraph(attributePaths = {
            "chatRoomMember",
            "chatRoomMember.chatRoom",
            "chatRoomMember.user"
    })
    List<OpenChatMemberProfile>
    findByChatRoomMemberChatRoomIdAndChatRoomMemberActiveTrueAndChatRoomMemberDeletedAtIsNullOrderByChatRoomMemberJoinedAtAsc(
            Long chatRoomId
    );

    @EntityGraph(attributePaths = {
            "chatRoomMember",
            "chatRoomMember.chatRoom",
            "chatRoomMember.user"
    })
    List<OpenChatMemberProfile>
    findByChatRoomMemberChatRoomIdAndChatRoomMemberUserIdIn(
            Long chatRoomId,
            Collection<Long> userIds
    );
}
