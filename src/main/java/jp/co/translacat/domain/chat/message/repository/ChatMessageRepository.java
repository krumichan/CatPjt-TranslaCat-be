package jp.co.translacat.domain.chat.message.repository;

import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageSenderType;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, Long>,
        ChatMessageRepositoryCustom {

    @EntityGraph(attributePaths = {
            "chatRoom",
            "senderUser",
            "senderAiMember",
            "senderAiMember.aiAgent"
    })
    Optional<ChatMessage> findByIdAndDeletedAtIsNull(Long id);

    Optional<ChatMessage> findByIdAndChatRoomIdAndDeletedAtIsNull(
            Long id,
            Long chatRoomId
    );

    @EntityGraph(attributePaths = {
            "chatRoom",
            "senderUser",
            "senderAiMember",
            "senderAiMember.aiAgent"
    })
    List<ChatMessage> findByIdInAndDeletedAtIsNull(
            Collection<Long> ids
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

    @EntityGraph(attributePaths = {
            "chatRoom",
            "senderUser",
            "senderAiMember",
            "senderAiMember.aiAgent"
    })
    List<ChatMessage> findByChatRoomIdAndStatusAndDeletedAtIsNullOrderByIdDesc(
            Long chatRoomId,
            ChatMessageStatus status,
            Pageable pageable
    );

    boolean existsByAiRequestId(String aiRequestId);
}
