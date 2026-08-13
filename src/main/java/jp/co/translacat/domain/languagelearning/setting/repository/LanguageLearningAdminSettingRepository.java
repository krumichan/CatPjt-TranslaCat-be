package jp.co.translacat.domain.languagelearning.setting.repository;

import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LanguageLearningAdminSettingRepository extends JpaRepository<LanguageLearningAdminSetting, String> {}
