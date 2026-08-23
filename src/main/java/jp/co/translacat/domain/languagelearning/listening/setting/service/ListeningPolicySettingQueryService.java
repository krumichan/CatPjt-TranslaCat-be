package jp.co.translacat.domain.languagelearning.listening.setting.service;

import jp.co.translacat.domain.languagelearning.listening.setting.entity.ListeningPolicySetting;
import jp.co.translacat.domain.languagelearning.listening.setting.repository.ListeningPolicySettingRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListeningPolicySettingQueryService {

    private final ListeningPolicySettingRepository repository;

    public ListeningPolicySetting get() {
        return repository.findById(ListeningPolicySetting.DEFAULT_ID)
                .orElseGet(ListeningPolicySetting::createDefault);
    }
}
