package jp.co.translacat.domain.languagelearning.speaking.session.model;

public record SpeakingSessionPolicySnapshot(
        boolean speakingEvaluationEnabled,
        int dailySpeakingHardLimitMinutes,
        int dailySpeakingSessionLimit,
        int maxSessionMinutes,
        int maxTurns,
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

    public long maxSessionSeconds() {
        return maxSessionMinutes * 60L;
    }

    public long dailySpeakingHardLimitSeconds() {
        return dailySpeakingHardLimitMinutes * 60L;
    }
}
