package jp.co.translacat.domain.languagelearning.setting.port;

import jp.co.translacat.domain.languagelearning.setting.dto.request.UserSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.UserSettingResponseDto;

/**
 * 외부 Settings 요청을 Ktor에 전달한다. 정책 승격/변경/생성의 소유자는 Ktor다.
 */
public interface UserSettingsGateway {
    UserSettingResponseDto get(Long userId);

    UserSettingResponseDto update(Long userId, UserSettingUpdateRequestDto request);

}
