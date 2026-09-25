package jp.co.translacat.infrastructure.languagelearning.gateway;

import jp.co.translacat.domain.languagelearning.setting.dto.request.UserSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.UserSettingResponseDto;
import jp.co.translacat.domain.languagelearning.setting.model.ConfiguredLanguagePair;
import jp.co.translacat.domain.languagelearning.setting.model.UserSettingsSnapshot;
import jp.co.translacat.domain.languagelearning.setting.port.UserSettingsGateway;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class RemoteUserSettingsGateway implements UserSettingsGateway {
    private final RemoteSettingsAccess access;

    public RemoteUserSettingsGateway(RemoteSettingsAccess access) {
        this.access = access;
    }

    @Override
    public UserSettingsSnapshot getSnapshot(Long userId) {
        var dto = access.forUser(userId).getUserSnapshot(userId);
        if (!userId.equals(dto.userId()) || dto.learningDate() == null) {
            throw new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_SETTINGS_CONTRACT_ERROR",
                    "설정 snapshot의 사용자 또는 날짜가 일치하지 않습니다.");
        }
        return new UserSettingsSnapshot(dto.userId(), dto.learningDate(), dto.revision(), dto.settings());
    }

    @Override
    public UserSettingResponseDto get(Long userId) {
        return access.forUser(userId).getUserSettings(userId);
    }

    @Override
    public UserSettingResponseDto update(Long userId, UserSettingUpdateRequestDto request) {
        return access.forUser(userId).updateUserSettings(userId, request);
    }

    @Override
    public LocalDate resolveToday(Long userId) {
        // 이 호출은 Ktor에서 행을 만들거나 pending을 승격하지 않는다.
        return access.client().resolveLearningDate(userId).date();
    }

    @Override
    public List<ConfiguredLanguagePair> configuredLanguagePairs() {
        return List.copyOf(access.client().configuredLanguagePairs().pairs());
    }
}
