package jp.co.translacat.domain.languagelearning.setting.audit.repository;

import jp.co.translacat.domain.languagelearning.setting.audit.entity.LanguageLearningAdminSettingAudit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LanguageLearningAdminSettingAuditRepository
        extends JpaRepository<LanguageLearningAdminSettingAudit, Long> {
}
