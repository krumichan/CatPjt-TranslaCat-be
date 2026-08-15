package jp.co.translacat.domain.languagelearning.setting.dto.request;

public record AdminSettingUpdateRequestDto(
        Integer defaultDailySentenceCount,
        Integer minDailySentenceCount,
        Integer maxDailySentenceCount,
        Integer dailyKeywordMaxCount,
        Integer reviewAvailableDays,
        Integer levelRecheckRecommendationDays,
        Boolean adaptiveWritingEnabled,
        Boolean aiEvaluationEnabled,
        Boolean speakingEnabled,
        Boolean speakingEvaluationEnabled,
        Integer defaultDailySpeakingGoalMinutes,
        Integer minDailySpeakingGoalMinutes,
        Integer maxDailySpeakingGoalMinutes,
        Integer dailySpeakingHardLimitMinutes,
        Integer dailySpeakingSessionLimit,
        Integer maxSessionMinutes,
        Integer maxTurnsPerSession,
        Double minValidAudioSeconds,
        Integer maxTurnAudioSeconds,
        Long maxAudioFileBytes,
        Integer rawAudioRetentionDays,
        Integer reportedAudioRetentionDays,
        Integer activeSessionResumeHours,
        Integer automaticRetryLimitPerStage,
        Integer manualRetryLimitPerStage,
        Integer sttTimeoutSeconds,
        Integer ttsTimeoutSeconds,
        Integer evaluationTimeoutSeconds
) {
    public AdminSettingUpdateRequestDto(
            Integer defaultDailySentenceCount,
            Integer minDailySentenceCount,
            Integer maxDailySentenceCount,
            Integer dailyKeywordMaxCount,
            Integer reviewAvailableDays,
            Integer levelRecheckRecommendationDays,
            Boolean adaptiveWritingEnabled,
            Boolean aiEvaluationEnabled
    ) {
        this(
                defaultDailySentenceCount,
                minDailySentenceCount,
                maxDailySentenceCount,
                dailyKeywordMaxCount,
                reviewAvailableDays,
                levelRecheckRecommendationDays,
                adaptiveWritingEnabled,
                aiEvaluationEnabled,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null
        );
    }
}
