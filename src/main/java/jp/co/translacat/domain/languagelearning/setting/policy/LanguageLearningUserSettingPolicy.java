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
            throw new BusinessException(
                    "언어 코드가 유효하지 않습니다.",
                    LanguageLearningErrorCode.SETTING_INVALID
            );
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
            throw new BusinessException(
                    "Timezone이 유효하지 않습니다.",
                    LanguageLearningErrorCode.SETTING_INVALID
            );
        }
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
            throw new BusinessException(
                    "Daily Sentence Count가 관리자 허용 범위를 벗어났습니다.",
                    LanguageLearningErrorCode.SETTING_INVALID
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

        throw new BusinessException(
                "Origin Language와 Learning Language는 달라야 합니다.",
                LanguageLearningErrorCode.SETTING_INVALID
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
}
