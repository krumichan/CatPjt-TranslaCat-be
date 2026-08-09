package jp.co.translacat.domain.chat.ai.repository;

import jp.co.translacat.domain.chat.ai.entity.ChatAiAgent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatAiAgentRepository extends JpaRepository<ChatAiAgent, Long> {

    Optional<ChatAiAgent> findByIdAndActiveTrueAndDeletedAtIsNull(Long id);
}
