package jp.co.translacat.domain.languagelearning.setting.model;

import jp.co.translacat.domain.languagelearning.setting.dto.response.AdminSettingResponseDto;
import java.util.Objects;

/** 현재 Ktor 정책의 값만 보관한다. 기본값 생성/정책 변경/DB 저장은 Ktor 책임이다. */
public record AdminSettingsSnapshot(AdminSettingResponseDto settings) {
    public AdminSettingsSnapshot {
        Objects.requireNonNull(settings, "관리자 설정이 없습니다.");
    }

    public int getDefaultDailySentenceCount() { return settings.defaultDailySentenceCount(); }
    public int getMinDailySentenceCount() { return settings.minDailySentenceCount(); }
    public int getMaxDailySentenceCount() { return settings.maxDailySentenceCount(); }
    public int getDailyKeywordMaxCount() { return settings.dailyKeywordMaxCount(); }
    public int getReviewAvailableDays() { return settings.reviewAvailableDays(); }
    public int getLevelRecheckRecommendationDays() { return settings.levelRecheckRecommendationDays(); }
    public boolean isAdaptiveWritingEnabled() { return settings.adaptiveWritingEnabled(); }
    public boolean isAiEvaluationEnabled() { return settings.aiEvaluationEnabled(); }
    public boolean isSpeakingEnabled() { return settings.speakingEnabled(); }
    public boolean isSpeakingEvaluationEnabled() { return settings.speakingEvaluationEnabled(); }
    public int getDefaultDailySpeakingGoalMinutes() { return settings.defaultDailySpeakingGoalMinutes(); }
    public int getMinDailySpeakingGoalMinutes() { return settings.minDailySpeakingGoalMinutes(); }
    public int getMaxDailySpeakingGoalMinutes() { return settings.maxDailySpeakingGoalMinutes(); }
    public int getDailySpeakingHardLimitMinutes() { return settings.dailySpeakingHardLimitMinutes(); }
    public int getDailySpeakingSessionLimit() { return settings.dailySpeakingSessionLimit(); }
    public int getMaxSessionMinutes() { return settings.maxSessionMinutes(); }
    public int getMaxTurnsPerSession() { return settings.maxTurnsPerSession(); }
    public double getMinValidAudioSeconds() { return settings.minValidAudioSeconds(); }
    public int getMaxTurnAudioSeconds() { return settings.maxTurnAudioSeconds(); }
    public long getMaxAudioFileBytes() { return settings.maxAudioFileBytes(); }
    public int getRawAudioRetentionDays() { return settings.rawAudioRetentionDays(); }
    public int getReportedAudioRetentionDays() { return settings.reportedAudioRetentionDays(); }
    public int getActiveSessionResumeHours() { return settings.activeSessionResumeHours(); }
    public int getAutomaticRetryLimitPerStage() { return settings.automaticRetryLimitPerStage(); }
    public int getManualRetryLimitPerStage() { return settings.manualRetryLimitPerStage(); }
    public int getSttTimeoutSeconds() { return settings.sttTimeoutSeconds(); }
    public int getTtsTimeoutSeconds() { return settings.ttsTimeoutSeconds(); }
    public int getEvaluationTimeoutSeconds() { return settings.evaluationTimeoutSeconds(); }
    public int getLevelTestQuestionPoolTargetSize() { return settings.levelTestQuestionPoolTargetSize(); }
    public boolean isLevelTestQuestionPoolReplenishmentEnabled() { return settings.levelTestQuestionPoolReplenishmentEnabled(); }
    public int resolvedLevelTestQuestionPoolTargetSize() { return settings.levelTestQuestionPoolTargetSize(); }
    public boolean resolvedLevelTestQuestionPoolReplenishmentEnabled() { return settings.levelTestQuestionPoolReplenishmentEnabled(); }

    /** 전달받은 동일 snapshot의 범위만 사용한다. 별도의 기본 정책을 만들지 않는다. */
    public int clampDailySentenceCount(int value) {
        return Math.max(settings.minDailySentenceCount(), Math.min(settings.maxDailySentenceCount(), value));
    }
}
