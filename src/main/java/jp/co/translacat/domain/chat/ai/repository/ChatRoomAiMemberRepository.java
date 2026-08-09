package jp.co.translacat.domain.chat.ai.repository;

import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatRoomAiMemberRepository extends JpaRepository<ChatRoomAiMember, Long> {

    @EntityGraph(attributePaths = {"chatRoom", "aiAgent"})
    List<ChatRoomAiMember> findByChatRoomIdAndActiveTrueAndDeletedAtIsNullOrderByJoinedAtAsc(
            Long chatRoomId
    );

    @EntityGraph(attributePaths = {"chatRoom", "aiAgent"})
    Optional<ChatRoomAiMember> findByIdAndChatRoomIdAndActiveTrueAndDeletedAtIsNull(
            Long id,
            Long chatRoomId
    );

    List<ChatRoomAiMember> findByChatRoomIdInAndActiveTrueAndDeletedAtIsNull(
            Collection<Long> chatRoomIds
    );

    long countByChatRoomIdAndActiveTrueAndDeletedAtIsNull(Long chatRoomId);

    boolean existsByChatRoomIdAndAiAgentIdAndActiveTrueAndDeletedAtIsNull(
            Long chatRoomId,
            Long aiAgentId
    );
}
