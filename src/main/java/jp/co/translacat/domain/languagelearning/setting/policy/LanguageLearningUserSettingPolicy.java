package jp.co.translacat.domain.languagelearning.setting.policy;

import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
public class LanguageLearningUserSettingPolicy {

    public String cleanLanguage(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.trim();
        if (cleaned.length() < 2 || cleaned.length() > 20) {
            throw invalid("언어 코드가 유효하지 않습니다.");
        }

        return cleaned;
    }

    public String cleanTimezone(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.trim();
        try {
            ZoneId.of(cleaned);
            return cleaned;
        } catch (Exception e) {
            throw invalid("Timezone이 유효하지 않습니다.");
        }
    }

    public String cleanVoiceId(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.trim();
        if (cleaned.isBlank() || cleaned.length() > 100) {
            throw invalid("Speaking Voice가 유효하지 않습니다.");
        }

        return cleaned;
    }

    public String cleanPlaybackSpeed(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.trim().toUpperCase();
        if (!cleaned.equals("NORMAL") && !cleaned.equals("SLOW")) {
            throw invalid("Speaking 재생 속도가 유효하지 않습니다.");
        }

        return cleaned;
    }

    public void validateSentenceCount(
            Integer sentenceCount,
            LanguageLearningAdminSetting adminSetting
    ) {
        if (sentenceCount == null) {
            return;
        }

        boolean outOfRange = sentenceCount
                < adminSetting.getMinDailySentenceCount()
                || sentenceCount > adminSetting.getMaxDailySentenceCount();
        if (outOfRange) {
            throw invalid(
                    "Daily Sentence Count가 관리자 허용 범위를 벗어났습니다."
            );
        }
    }

    public void validateSpeakingGoal(
            Integer goalMinutes,
            LanguageLearningAdminSetting adminSetting
    ) {
        if (goalMinutes == null) {
            return;
        }

        boolean outOfRange = goalMinutes
                < adminSetting.getMinDailySpeakingGoalMinutes()
                || goalMinutes > adminSetting.getMaxDailySpeakingGoalMinutes();

        if (outOfRange) {
            throw invalid(
                    "Daily Speaking Goal이 관리자 허용 범위를 벗어났습니다."
            );
        }
    }

    public void validateLanguagePair(
            String originLanguage,
            String learningLanguage
    ) {
        if (originLanguage == null || learningLanguage == null) {
            return;
        }
        if (!originLanguage.equalsIgnoreCase(learningLanguage)) {
            return;
        }

        throw invalid(
                "Origin Language와 Learning Language는 달라야 합니다."
        );
    }

    public String resolveNextOriginLanguage(
            LanguageLearningUserSetting setting,
            String requestedLanguage
    ) {
        if (requestedLanguage != null) {
            return requestedLanguage;
        }
        if (setting.getPendingOriginLanguage() != null) {
            return setting.getPendingOriginLanguage();
        }

        return setting.getOriginLanguage();
    }

    public String resolveNextLearningLanguage(
            LanguageLearningUserSetting setting,
            String requestedLanguage
    ) {
        if (requestedLanguage != null) {
            return requestedLanguage;
        }
        if (setting.getPendingLearningLanguage() != null) {
            return setting.getPendingLearningLanguage();
        }

        return setting.getLearningLanguage();
    }

    public boolean isFirstConfiguration(
            LanguageLearningUserSetting setting
    ) {
        return setting.getOriginLanguage() == null
                && setting.getLearningLanguage() == null
                && setting.getPendingEffectiveDate() == null;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.SETTING_INVALID
        );
    }
}
