package jp.co.translacat.domain.languagelearning.setting.dto.response;

import java.time.LocalDate;

public record UserSettingResponseDto(
        String originLanguage,
        String learningLanguage,
        String timezone,
        int dailySentenceCount,
        String pendingOriginLanguage,
        String pendingLearningLanguage,
        String pendingTimezone,
        Integer pendingDailySentenceCount,
        LocalDate pendingEffectiveDate,
        int minDailySentenceCount,
        int maxDailySentenceCount,
        boolean configured
) {
}
