package jp.co.translacat.domain.languagelearning.setting.facade;

import jp.co.translacat.domain.languagelearning.setting.dto.request.AdminSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.AdminSettingResponseDto;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningAdminSettingCommandService;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningAdminSettingQueryService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminLanguageLearningSettingFacade {

    private final LanguageLearningAdminSettingQueryService queryService;
    private final LanguageLearningAdminSettingCommandService commandService;

    public AdminSettingResponseDto get() {
        return queryService.getSettings();
    }

    public AdminSettingResponseDto update(
            Long adminUserId,
            AdminSettingUpdateRequestDto request
    ) {
        LanguageLearningAdminSetting setting = commandService.update(
                adminUserId,
                request
        );
        return queryService.toResponse(setting);
    }
}
