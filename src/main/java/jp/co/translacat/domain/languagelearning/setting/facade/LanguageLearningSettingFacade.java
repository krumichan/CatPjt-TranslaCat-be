package jp.co.translacat.domain.languagelearning.setting.facade;

import jp.co.translacat.domain.languagelearning.setting.dto.request.UserSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.UserSettingResponseDto;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningAdminSettingQueryService;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingCommandService;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LanguageLearningSettingFacade {

    private final LanguageLearningUserSettingQueryService queryService;
    private final LanguageLearningUserSettingCommandService commandService;
    private final LanguageLearningAdminSettingQueryService adminQueryService;

    public UserSettingResponseDto get(Long userId) {
        return queryService.get(userId);
    }

    public UserSettingResponseDto update(
            Long userId,
            UserSettingUpdateRequestDto request
    ) {
        LanguageLearningUserSetting setting = commandService.update(
                userId,
                request
        );
        LanguageLearningAdminSetting admin =
                adminQueryService.getOrCreateEntity();

        return queryService.toResponse(setting, admin);
    }
}
