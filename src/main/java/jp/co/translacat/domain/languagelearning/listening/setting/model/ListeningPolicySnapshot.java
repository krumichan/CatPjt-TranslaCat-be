package jp.co.translacat.domain.languagelearning.listening.setting.model;

/**
 * Ktor가 소유한 Listening 운영 정책의 불변 snapshot. BE의 로컬 fallback은 없다.
 */
public record ListeningPolicySnapshot(
        boolean enabled,
        int defaultItemCount,
        int minItemCount,
        int maxItemCount,
        int hardItemLimit,
        int referenceAudioMaxSeconds,
        int repeatAudioMaxSeconds,
        long maxAudioFileBytes,
        int maxRerecordCount,
        int resumeHours,
        int referenceAudioRetentionDays,
        int userAudioRetentionDays,
        int reportedAudioRetentionDays,
        int automaticRetryLimit,
        int manualRetryLimit,
        int practiceAttemptLimit,
        String profilePolicyVersion,
        String modelConfigVersion,
        boolean referenceTtsRegenerationEnabled
) {
}
