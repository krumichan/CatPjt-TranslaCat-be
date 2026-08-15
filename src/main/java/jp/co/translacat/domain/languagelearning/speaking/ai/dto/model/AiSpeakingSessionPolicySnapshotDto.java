package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

public record AiSpeakingSessionPolicySnapshotDto(
        int maxSessionMinutes,
        int maxTurns,
        double minValidAudioSeconds,
        int maxTurnAudioSeconds,
        long maxAudioFileBytes,
        int automaticRetryLimitPerStage,
        int manualRetryLimitPerStage
) {
}
