package jp.co.translacat.domain.chat.message.repository;

import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Optional<ChatMessage> findByIdAndDeletedAtIsNull(Long id);

    Optional<ChatMessage> findByIdAndChatRoomIdAndDeletedAtIsNull(
            Long id,
            Long chatRoomId
    );

    List<ChatMessage> findTop50ByChatRoomIdAndStatusAndDeletedAtIsNullOrderByIdDesc(
            Long chatRoomId,
            ChatMessageStatus status
    );

    List<ChatMessage> findTop50ByChatRoomIdAndStatusAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
            Long chatRoomId,
            ChatMessageStatus status,
            Long cursorId
    );

    List<ChatMessage> findByChatRoomAndStatusAndDeletedAtIsNullOrderByIdAsc(
            ChatRoom chatRoom,
            ChatMessageStatus status
    );

    long countByChatRoomIdAndStatusAndDeletedAtIsNull(
            Long chatRoomId,
            ChatMessageStatus status
    );
}