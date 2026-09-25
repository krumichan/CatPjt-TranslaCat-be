package jp.co.translacat.domain.languagelearning.setting.model;

import jp.co.translacat.domain.languagelearning.setting.dto.response.UserSettingResponseDto;

import java.time.LocalDate;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Ktor에서 승격/보정한 불변 설정이다. BE JPA Entity 및 저장 메서드는 포함하지 않는다.
 */
public record UserSettingsSnapshot(Long userId, LocalDate learningDate, java.time.LocalDateTime revision,
                                   UserSettingResponseDto settings) {
    public UserSettingsSnapshot {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("userId는 양수여야 합니다.");
        Objects.requireNonNull(learningDate, "학습 날짜가 없습니다.");
        Objects.requireNonNull(revision, "설정 revision이 없습니다.");
        Objects.requireNonNull(settings, "사용자 설정이 없습니다.");
        Objects.requireNonNull(settings.timezone(), "활성 timezone이 없습니다.");
        Objects.requireNonNull(settings.defaultListeningTaskTypes(), "Listening Task 설정이 없습니다.");
    }

    public String getOriginLanguage() {
        return settings.originLanguage();
    }

    public String getLearningLanguage() {
        return settings.learningLanguage();
    }

    public String getTimezone() {
        return settings.timezone();
    }

    public int getDailySentenceCount() {
        return settings.dailySentenceCount();
    }

    public int getDailySpeakingGoalMinutes() {
        return settings.dailySpeakingGoalMinutes();
    }

    public String getSpeakingVoiceId() {
        return settings.speakingVoiceId();
    }

    public String getSpeakingPlaybackSpeed() {
        return settings.speakingPlaybackSpeed();
    }

    public int getDailyListeningGoalCount() {
        return settings.dailyListeningGoalCount();
    }

    public String getPendingOriginLanguage() {
        return settings.pendingOriginLanguage();
    }

    public String getPendingLearningLanguage() {
        return settings.pendingLearningLanguage();
    }

    public String getPendingTimezone() {
        return settings.pendingTimezone();
    }

    public Integer getPendingDailySentenceCount() {
        return settings.pendingDailySentenceCount();
    }

    public Integer getPendingDailySpeakingGoalMinutes() {
        return settings.pendingDailySpeakingGoalMinutes();
    }

    public Integer getPendingDailyListeningGoalCount() {
        return settings.pendingDailyListeningGoalCount();
    }

    public LocalDate getPendingEffectiveDate() {
        return settings.pendingEffectiveDate();
    }

    public int getMinDailySentenceCount() {
        return settings.minDailySentenceCount();
    }

    public int getMaxDailySentenceCount() {
        return settings.maxDailySentenceCount();
    }

    public int getMinDailySpeakingGoalMinutes() {
        return settings.minDailySpeakingGoalMinutes();
    }

    public int getMaxDailySpeakingGoalMinutes() {
        return settings.maxDailySpeakingGoalMinutes();
    }

    public int getMinDailyListeningGoalCount() {
        return settings.minDailyListeningGoalCount();
    }

    public int getMaxDailyListeningGoalCount() {
        return settings.maxDailyListeningGoalCount();
    }

    public boolean isConfigured() {
        return settings.configured();
    }

    public String getDefaultListeningTaskTypesJson() {
        return settings.defaultListeningTaskTypes().stream()
                .map(value -> "\"" + value.name() + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
