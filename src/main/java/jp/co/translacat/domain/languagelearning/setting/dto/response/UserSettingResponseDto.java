package jp.co.translacat.domain.languagelearning.setting.dto.response;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;

import java.time.LocalDate;
import java.util.List;

public record UserSettingResponseDto(
        String originLanguage,
        String learningLanguage,
        String timezone,
        int dailySentenceCount,
        int dailySpeakingGoalMinutes,
        String speakingVoiceId,
        String speakingPlaybackSpeed,
        int dailyListeningGoalCount,
        List<ListeningTaskType> defaultListeningTaskTypes,
        String pendingOriginLanguage,
        String pendingLearningLanguage,
        String pendingTimezone,
        Integer pendingDailySentenceCount,
        Integer pendingDailySpeakingGoalMinutes,
        Integer pendingDailyListeningGoalCount,
        LocalDate pendingEffectiveDate,
        int minDailySentenceCount,
        int maxDailySentenceCount,
        int minDailySpeakingGoalMinutes,
        int maxDailySpeakingGoalMinutes,
        int minDailyListeningGoalCount,
        int maxDailyListeningGoalCount,
        boolean configured
) {
}
