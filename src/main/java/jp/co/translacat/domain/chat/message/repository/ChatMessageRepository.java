package jp.co.translacat.domain.chat.message.repository;

import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageSenderType;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, Long> {

    Optional<ChatMessage> findByIdAndDeletedAtIsNull(Long id);

    Optional<ChatMessage> findByIdAndChatRoomIdAndDeletedAtIsNull(
            Long id,
            Long chatRoomId
    );

    Optional<ChatMessage>
    findTopByChatRoomIdAndStatusAndDeletedAtIsNullOrderByIdDesc(
            Long chatRoomId,
            ChatMessageStatus status
    );

    List<ChatMessage>
    findTop101ByChatRoomIdAndStatusAndDeletedAtIsNullOrderByIdDesc(
            Long chatRoomId,
            ChatMessageStatus status
    );

    List<ChatMessage>
    findTop101ByChatRoomIdAndStatusAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
            Long chatRoomId,
            ChatMessageStatus status,
            Long cursorId
    );

    List<ChatMessage>
    findTop101ByChatRoomIdAndStatusAndDeletedAtIsNullAndCreatedAtGreaterThanEqualOrderByIdDesc(
            Long chatRoomId,
            ChatMessageStatus status,
            LocalDateTime joinedAt
    );

    List<ChatMessage>
    findTop101ByChatRoomIdAndStatusAndDeletedAtIsNullAndCreatedAtGreaterThanEqualAndIdLessThanOrderByIdDesc(
            Long chatRoomId,
            ChatMessageStatus status,
            LocalDateTime joinedAt,
            Long cursorId
    );

    List<ChatMessage>
    findByChatRoomAndStatusAndDeletedAtIsNullOrderByIdAsc(
            ChatRoom chatRoom,
            ChatMessageStatus status
    );

    long countByChatRoomIdAndStatusAndDeletedAtIsNull(
            Long chatRoomId,
            ChatMessageStatus status
    );

    @EntityGraph(attributePaths = {
            "chatRoom",
            "senderUser",
            "senderAiMember",
            "senderAiMember.aiAgent"
    })
    @Query("""
            select message
            from ChatMessage message
            where message.id = :messageId
              and message.deletedAt is null
            """)
    Optional<ChatMessage> findWithSenderById(
            @Param("messageId") Long messageId
    );

    @EntityGraph(attributePaths = {
            "chatRoom",
            "senderUser",
            "senderAiMember",
            "senderAiMember.aiAgent"
    })
    List<ChatMessage> findByChatRoomIdAndStatusAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
            Long chatRoomId,
            ChatMessageStatus status,
            Long messageId,
            Pageable pageable
    );

    Optional<ChatMessage> findTopByChatRoomIdAndSenderTypeAndStatusAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
            Long chatRoomId,
            ChatMessageSenderType senderType,
            ChatMessageStatus status,
            Long messageId
    );

    long countByChatRoomIdAndSenderTypeAndStatusAndDeletedAtIsNullAndIdGreaterThanAndIdLessThanEqual(
            Long chatRoomId,
            ChatMessageSenderType senderType,
            ChatMessageStatus status,
            Long minExclusiveMessageId,
            Long maxInclusiveMessageId
    );

    long countByChatRoomIdAndSenderTypeAndStatusAndDeletedAtIsNullAndIdLessThanEqual(
            Long chatRoomId,
            ChatMessageSenderType senderType,
            ChatMessageStatus status,
            Long maxInclusiveMessageId
    );

    List<ChatMessage> findByChatRoomIdAndSenderUserIdAndSenderTypeAndStatusAndDeletedAtIsNullAndCreatedAtGreaterThanEqualAndIdLessThanOrderByIdDesc(
            Long chatRoomId,
            Long senderUserId,
            ChatMessageSenderType senderType,
            ChatMessageStatus status,
            LocalDateTime createdAt,
            Long maxExclusiveMessageId
    );

    boolean existsByAiRequestId(String aiRequestId);
}
