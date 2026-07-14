package jp.co.translacat.domain.chat.language.repository;

import jp.co.translacat.domain.chat.language.entity.UserChatLanguageSetting;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserChatLanguageSettingRepository
        extends JpaRepository<UserChatLanguageSetting, Long> {

    @EntityGraph(attributePaths = {"user"})
    Optional<UserChatLanguageSetting> findByUserId(Long userId);
}
