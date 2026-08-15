package jp.co.translacat.domain.languagelearning.setting.audit.service;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.setting.audit.entity.LanguageLearningAdminSettingAudit;
import jp.co.translacat.domain.languagelearning.setting.audit.repository.LanguageLearningAdminSettingAuditRepository;
import jp.co.translacat.domain.languagelearning.setting.dto.response.AdminSettingResponseDto;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LanguageLearningAdminSettingAuditCommandService {

    private final LanguageLearningAdminSettingAuditRepository repository;
    private final LanguageLearningJsonCodec jsonCodec;

    public void record(
            Long adminUserId,
            AdminSettingResponseDto before,
            AdminSettingResponseDto after
    ) {
        repository.save(
                LanguageLearningAdminSettingAudit.create(
                        adminUserId,
                        jsonCodec.write(before),
                        jsonCodec.write(after)
                )
        );
    }
}
