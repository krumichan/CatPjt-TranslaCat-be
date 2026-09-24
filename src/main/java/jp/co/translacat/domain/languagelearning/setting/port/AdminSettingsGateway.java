package jp.co.translacat.domain.languagelearning.setting.port;

import jp.co.translacat.domain.languagelearning.setting.dto.request.AdminSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.AdminSettingResponseDto;
import jp.co.translacat.domain.languagelearning.setting.model.AdminSettingsSnapshot;

public interface AdminSettingsGateway {
    AdminSettingsSnapshot getSnapshot();
    AdminSettingResponseDto getSettings(Long adminUserId);
    AdminSettingResponseDto update(Long adminUserId, AdminSettingUpdateRequestDto request);

    default int getLevelTestQuestionPoolTargetSize() { return getSnapshot().resolvedLevelTestQuestionPoolTargetSize(); }
    default boolean isLevelTestQuestionPoolReplenishmentEnabled() { return getSnapshot().resolvedLevelTestQuestionPoolReplenishmentEnabled(); }
}
