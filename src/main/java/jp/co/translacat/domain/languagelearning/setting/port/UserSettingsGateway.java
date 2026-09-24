package jp.co.translacat.domain.languagelearning.setting.port;

import jp.co.translacat.domain.languagelearning.setting.dto.request.UserSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.UserSettingResponseDto;
import jp.co.translacat.domain.languagelearning.setting.model.ConfiguredLanguagePair;
import jp.co.translacat.domain.languagelearning.setting.model.UserSettingsSnapshot;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;

/** 학습 기능은 이 계약만 사용한다. 정책 승격/변경/생성의 소유자는 Ktor다. */
public interface UserSettingsGateway {
    UserSettingsSnapshot getSnapshot(Long userId);
    UserSettingResponseDto get(Long userId);
    UserSettingResponseDto update(Long userId, UserSettingUpdateRequestDto request);
    LocalDate resolveToday(Long userId);
    List<ConfiguredLanguagePair> configuredLanguagePairs();

    default LocalDate resolveToday(UserSettingsSnapshot snapshot) { return snapshot.learningDate(); }

    default void requireConfigured(UserSettingsSnapshot snapshot) {
        if (!snapshot.isConfigured()) {
            throw new BusinessException("Origin Language와 Learning Language 설정이 필요합니다.",
                    LanguageLearningErrorCode.SETTING_NOT_CONFIGURED);
        }
    }
}
