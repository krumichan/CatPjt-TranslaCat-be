package jp.co.translacat.domain.chat.ai.repository;

import jp.co.translacat.domain.chat.ai.entity.ChatAiSystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatAiSystemSettingRepository
        extends JpaRepository<ChatAiSystemSetting, String> {
}
