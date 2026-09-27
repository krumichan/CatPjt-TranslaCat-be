package jp.co.translacat.domain.languagelearning.setting.port;

import jp.co.translacat.domain.languagelearning.setting.dto.request.AdminSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.AdminSettingResponseDto;

public interface AdminSettingsGateway {
    AdminSettingResponseDto getSettings(Long adminUserId);

    AdminSettingResponseDto update(Long adminUserId, AdminSettingUpdateRequestDto request);

}
