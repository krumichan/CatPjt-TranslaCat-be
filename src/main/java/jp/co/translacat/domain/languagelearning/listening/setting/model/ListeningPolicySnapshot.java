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
    public boolean isEnabled() {
        return enabled;
    }

    public int getDefaultItemCount() {
        return defaultItemCount;
    }

    public int getMinItemCount() {
        return minItemCount;
    }

    public int getMaxItemCount() {
        return maxItemCount;
    }

    public int getHardItemLimit() {
        return hardItemLimit;
    }

    public int getReferenceAudioMaxSeconds() {
        return referenceAudioMaxSeconds;
    }

    public int getRepeatAudioMaxSeconds() {
        return repeatAudioMaxSeconds;
    }

    public long getMaxAudioFileBytes() {
        return maxAudioFileBytes;
    }

    public int getMaxRerecordCount() {
        return maxRerecordCount;
    }

    public int getResumeHours() {
        return resumeHours;
    }

    public int getReferenceAudioRetentionDays() {
        return referenceAudioRetentionDays;
    }

    public int getUserAudioRetentionDays() {
        return userAudioRetentionDays;
    }

    public int getReportedAudioRetentionDays() {
        return reportedAudioRetentionDays;
    }

    public int getAutomaticRetryLimit() {
        return automaticRetryLimit;
    }

    public int getManualRetryLimit() {
        return manualRetryLimit;
    }

    public int getPracticeAttemptLimit() {
        return practiceAttemptLimit;
    }

    public String getProfilePolicyVersion() {
        return profilePolicyVersion;
    }

    public String getModelConfigVersion() {
        return modelConfigVersion;
    }

    public boolean isReferenceTtsRegenerationEnabled() {
        return referenceTtsRegenerationEnabled;
    }

    /**
     * 요청별 문항 수 선택은 전달받은 정책으로 검증한다. 저장 정책을 변경하지 않는다.
     */
    public int resolveItemCount(Integer requested) {
        int value = requested == null ? defaultItemCount : requested;
        if (value < minItemCount || value > maxItemCount || value > hardItemLimit) {
            throw new IllegalArgumentException("Listening Item Count가 허용 범위를 벗어났습니다.");
        }
        return value;
    }
}
