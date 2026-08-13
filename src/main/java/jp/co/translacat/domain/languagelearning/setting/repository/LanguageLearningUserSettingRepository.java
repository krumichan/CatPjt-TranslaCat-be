package jp.co.translacat.domain.languagelearning.setting.repository;

import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LanguageLearningUserSettingRepository extends JpaRepository<LanguageLearningUserSetting, Long> {
    Optional<LanguageLearningUserSetting> findByUserId(Long userId);
}
