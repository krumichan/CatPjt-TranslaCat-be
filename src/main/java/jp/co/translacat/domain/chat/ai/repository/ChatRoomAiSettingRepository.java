package jp.co.translacat.domain.chat.ai.repository;

import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiSetting;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatRoomAiSettingRepository extends JpaRepository<ChatRoomAiSetting, Long> {

    @EntityGraph(attributePaths = "chatRoom")
    Optional<ChatRoomAiSetting> findByChatRoomId(Long chatRoomId);

    @EntityGraph(attributePaths = "chatRoom")
    List<ChatRoomAiSetting> findByChatRoomIdIn(Collection<Long> chatRoomIds);
}
