package jp.co.translacat.infrastructure.languagelearning.gateway;

import jp.co.translacat.domain.languagelearning.setting.dto.request.UserSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.UserSettingResponseDto;
import jp.co.translacat.domain.languagelearning.setting.port.UserSettingsGateway;
import org.springframework.stereotype.Component;

@Component
public class RemoteUserSettingsGateway implements UserSettingsGateway {
    private final RemoteSettingsAccess access;

    public RemoteUserSettingsGateway(RemoteSettingsAccess access) {
        this.access = access;
    }

    @Override
    public UserSettingResponseDto get(Long userId) {
        return access.forUser(userId).getUserSettings(userId);
    }

    @Override
    public UserSettingResponseDto update(Long userId, UserSettingUpdateRequestDto request) {
        return access.forUser(userId).updateUserSettings(userId, request);
    }

}
