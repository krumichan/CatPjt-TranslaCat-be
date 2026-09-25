package jp.co.translacat.infrastructure.languagelearning.gateway;

import jp.co.translacat.domain.languagelearning.setting.dto.request.AdminSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.AdminSettingResponseDto;
import jp.co.translacat.domain.languagelearning.setting.model.AdminSettingsSnapshot;
import jp.co.translacat.domain.languagelearning.setting.port.AdminSettingsGateway;
import org.springframework.stereotype.Component;

@Component
public class RemoteAdminSettingsGateway implements AdminSettingsGateway {
    private final RemoteSettingsAccess access;

    public RemoteAdminSettingsGateway(RemoteSettingsAccess access) {
        this.access = access;
    }

    @Override
    public AdminSettingsSnapshot getSnapshot() {
        // 배치/학습 로직은 가짜 관리자 ID가 아니라 서비스 조회 토큰을 사용한다.
        return new AdminSettingsSnapshot(access.client().getAdminPolicy());
    }

    @Override
    public AdminSettingResponseDto getSettings(Long adminUserId) {
        return access.forUser(adminUserId).getAdminSettings(adminUserId);
    }

    @Override
    public AdminSettingResponseDto update(Long adminUserId, AdminSettingUpdateRequestDto request) {
        return access.forUser(adminUserId).updateAdminSettings(adminUserId, request);
    }
}
