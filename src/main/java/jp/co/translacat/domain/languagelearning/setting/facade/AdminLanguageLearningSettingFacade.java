package jp.co.translacat.domain.languagelearning.setting.facade;

import jp.co.translacat.domain.languagelearning.setting.dto.request.AdminSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.AdminSettingResponseDto;
import jp.co.translacat.domain.languagelearning.setting.port.AdminSettingsGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminLanguageLearningSettingFacade {
    private final AdminSettingsGateway settings;

    public AdminSettingResponseDto get(Long adminUserId) {
        return settings.getSettings(adminUserId);
    }

    public AdminSettingResponseDto update(Long adminUserId, AdminSettingUpdateRequestDto request) {
        return settings.update(adminUserId, request);
    }
}
