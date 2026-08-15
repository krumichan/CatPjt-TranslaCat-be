package jp.co.translacat.domain.languagelearning.setting.dto.response;

import java.time.LocalDate;

public record UserSettingResponseDto(
        String originLanguage,
        String learningLanguage,
        String timezone,
        int dailySentenceCount,
        int dailySpeakingGoalMinutes,
        String speakingVoiceId,
        String speakingPlaybackSpeed,
        String pendingOriginLanguage,
        String pendingLearningLanguage,
        String pendingTimezone,
        Integer pendingDailySentenceCount,
        Integer pendingDailySpeakingGoalMinutes,
        LocalDate pendingEffectiveDate,
        int minDailySentenceCount,
        int maxDailySentenceCount,
        int minDailySpeakingGoalMinutes,
        int maxDailySpeakingGoalMinutes,
        boolean configured
) {
}
