package jp.co.translacat.domain.chat.ai.repository;

import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomAiActivityRepository
        extends JpaRepository<ChatRoomAiActivity, Long>,
        ChatRoomAiActivityRepositoryCustom {

    Optional<ChatRoomAiActivity> findByChatRoomId(Long chatRoomId);
}