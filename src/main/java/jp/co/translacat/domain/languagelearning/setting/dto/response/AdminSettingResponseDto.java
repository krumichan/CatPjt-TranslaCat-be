package jp.co.translacat.domain.languagelearning.setting.dto.response;

public record AdminSettingResponseDto(
        int defaultDailySentenceCount,
        int minDailySentenceCount,
        int maxDailySentenceCount,
        int dailyKeywordMaxCount,
        int reviewAvailableDays,
        int levelRecheckRecommendationDays,
        boolean adaptiveWritingEnabled,
        boolean aiEvaluationEnabled,
        boolean speakingEnabled,
        boolean speakingEvaluationEnabled,
        int defaultDailySpeakingGoalMinutes,
        int minDailySpeakingGoalMinutes,
        int maxDailySpeakingGoalMinutes,
        int dailySpeakingHardLimitMinutes,
        int dailySpeakingSessionLimit,
        int maxSessionMinutes,
        int maxTurnsPerSession,
        double minValidAudioSeconds,
        int maxTurnAudioSeconds,
        long maxAudioFileBytes,
        int rawAudioRetentionDays,
        int reportedAudioRetentionDays,
        int activeSessionResumeHours,
        int automaticRetryLimitPerStage,
        int manualRetryLimitPerStage,
        int sttTimeoutSeconds,
        int ttsTimeoutSeconds,
        int evaluationTimeoutSeconds
) {
}
