package jp.co.translacat.domain.languagelearning.listening.setting.repository;

import jp.co.translacat.domain.languagelearning.listening.setting.entity.ListeningPolicySetting;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ListeningPolicySettingRepository
        extends JpaRepository<ListeningPolicySetting, String> {
}
