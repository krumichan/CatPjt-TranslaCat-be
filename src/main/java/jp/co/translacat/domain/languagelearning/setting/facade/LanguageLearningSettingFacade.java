package jp.co.translacat.domain.languagelearning.setting.facade;

import jp.co.translacat.domain.languagelearning.setting.dto.request.UserSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.UserSettingResponseDto;
import jp.co.translacat.domain.languagelearning.setting.port.UserSettingsGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LanguageLearningSettingFacade {
    private final UserSettingsGateway settings;

    public UserSettingResponseDto get(Long userId) { return settings.get(userId); }
    public UserSettingResponseDto update(Long userId, UserSettingUpdateRequestDto request) {
        return settings.update(userId, request);
    }
}
