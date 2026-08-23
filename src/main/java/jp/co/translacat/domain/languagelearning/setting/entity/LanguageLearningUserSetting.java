package jp.co.translacat.domain.languagelearning.setting.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@Table(
        name = "language_learning_user_setting",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_language_learning_user_setting_user",
                columnNames = "user_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LanguageLearningUserSetting extends BaseAuditable {

    private static final String DEFAULT_TIMEZONE = "Asia/Tokyo";
    private static final String DEFAULT_SPEAKING_VOICE = "Kore";
    private static final String DEFAULT_PLAYBACK_SPEED = "NORMAL";
    private static final int DEFAULT_DAILY_LISTENING_GOAL_COUNT = 5;
    private static final String DEFAULT_LISTENING_TASK_TYPES_JSON =
            "[\"DICTATION\"]";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(length = 20)
    private String originLanguage;

    @Column(length = 20)
    private String learningLanguage;

    @Column(length = 60, nullable = false)
    private String timezone;

    @Column(nullable = false)
    private int dailySentenceCount;

    @Column(nullable = false)
    private int dailySpeakingGoalMinutes;

    @Column(name = "daily_listening_goal_count", nullable = false)
    private int dailyListeningGoalCount;

    @Column(name = "default_listening_task_types", nullable = false, length = 200)
    private String defaultListeningTaskTypesJson;

    @Column(nullable = false, length = 100)
    private String speakingVoiceId;

    @Column(nullable = false, length = 20)
    private String speakingPlaybackSpeed;

    @Column(length = 20)
    private String pendingOriginLanguage;

    @Column(length = 20)
    private String pendingLearningLanguage;

    @Column(length = 60)
    private String pendingTimezone;

    private Integer pendingDailySentenceCount;
    private Integer pendingDailySpeakingGoalMinutes;

    @Column(name = "pending_daily_listening_goal_count")
    private Integer pendingDailyListeningGoalCount;

    private LocalDate pendingEffectiveDate;

    private LanguageLearningUserSetting(
            User user,
            int defaultDailySentenceCount
    ) {
        this.user = user;
        this.timezone = DEFAULT_TIMEZONE;
        this.dailySentenceCount = defaultDailySentenceCount;
        this.dailySpeakingGoalMinutes =
                LanguageLearningAdminSetting.DEFAULT_DAILY_SPEAKING_GOAL_MINUTES;
        this.dailyListeningGoalCount = DEFAULT_DAILY_LISTENING_GOAL_COUNT;
        this.defaultListeningTaskTypesJson = DEFAULT_LISTENING_TASK_TYPES_JSON;
        this.speakingVoiceId = DEFAULT_SPEAKING_VOICE;
        this.speakingPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;
    }

    public static LanguageLearningUserSetting create(
            User user,
            int defaultDailySentenceCount
    ) {
        return new LanguageLearningUserSetting(
                user,
                defaultDailySentenceCount
        );
    }

    public void initialize(
            String originLanguage,
            String learningLanguage,
            String timezone,
            Integer dailySentenceCount
    ) {
        initialize(
                originLanguage,
                learningLanguage,
                timezone,
                dailySentenceCount,
                null,
                null,
                null
        );
    }

    public void initialize(
            String originLanguage,
            String learningLanguage,
            String timezone,
            Integer dailySentenceCount,
            Integer dailySpeakingGoalMinutes,
            String speakingVoiceId,
            String speakingPlaybackSpeed
    ) {
        if (originLanguage != null) {
            this.originLanguage = originLanguage;
        }
        if (learningLanguage != null) {
            this.learningLanguage = learningLanguage;
        }
        if (timezone != null) {
            this.timezone = timezone;
        }
        if (dailySentenceCount != null) {
            this.dailySentenceCount = dailySentenceCount;
        }
        if (dailySpeakingGoalMinutes != null) {
            this.dailySpeakingGoalMinutes = dailySpeakingGoalMinutes;
        }
        updateSpeakingPlayback(speakingVoiceId, speakingPlaybackSpeed);
    }

    public void scheduleUpdate(
            String originLanguage,
            String learningLanguage,
            String timezone,
            Integer dailySentenceCount,
            LocalDate effectiveDate
    ) {
        scheduleUpdate(
                originLanguage,
                learningLanguage,
                timezone,
                dailySentenceCount,
                null,
                effectiveDate
        );
    }

    public void scheduleUpdate(
            String originLanguage,
            String learningLanguage,
            String timezone,
            Integer dailySentenceCount,
            Integer dailySpeakingGoalMinutes,
            LocalDate effectiveDate
    ) {
        if (originLanguage != null) {
            this.pendingOriginLanguage = originLanguage;
        }
        if (learningLanguage != null) {
            this.pendingLearningLanguage = learningLanguage;
        }
        if (timezone != null) {
            this.pendingTimezone = timezone;
        }
        if (dailySentenceCount != null) {
            this.pendingDailySentenceCount = dailySentenceCount;
        }
        if (dailySpeakingGoalMinutes != null) {
            this.pendingDailySpeakingGoalMinutes = dailySpeakingGoalMinutes;
        }

        this.pendingEffectiveDate = effectiveDate;
    }


    public void initializeListening(
            Integer dailyListeningGoalCount,
            String defaultListeningTaskTypesJson
    ) {
        if (dailyListeningGoalCount != null) {
            this.dailyListeningGoalCount = dailyListeningGoalCount;
        }
        updateDefaultListeningTaskTypes(defaultListeningTaskTypesJson);
    }

    public void scheduleListeningGoal(
            Integer dailyListeningGoalCount,
            LocalDate effectiveDate
    ) {
        if (dailyListeningGoalCount == null) {
            return;
        }
        this.pendingDailyListeningGoalCount = dailyListeningGoalCount;
        this.pendingEffectiveDate = effectiveDate;
    }

    public void updateDefaultListeningTaskTypes(String taskTypesJson) {
        if (taskTypesJson != null && !taskTypesJson.isBlank()) {
            this.defaultListeningTaskTypesJson = taskTypesJson;
        }
    }

    public void updateSpeakingPlayback(
            String speakingVoiceId,
            String speakingPlaybackSpeed
    ) {
        if (speakingVoiceId != null && !speakingVoiceId.isBlank()) {
            this.speakingVoiceId = speakingVoiceId.trim();
        }
        if (speakingPlaybackSpeed != null
                && !speakingPlaybackSpeed.isBlank()) {
            this.speakingPlaybackSpeed = speakingPlaybackSpeed.trim();
        }
    }

    public boolean promoteIfEffective(LocalDate date) {
        if (pendingEffectiveDate == null || pendingEffectiveDate.isAfter(date)) {
            return false;
        }

        applyPendingValues();
        clearPendingValues();

        return true;
    }

    public void clampActiveAndPending(
            int minCount,
            int maxCount
    ) {
        this.dailySentenceCount = clamp(
                dailySentenceCount,
                minCount,
                maxCount
        );

        if (pendingDailySentenceCount != null) {
            this.pendingDailySentenceCount = clamp(
                    pendingDailySentenceCount,
                    minCount,
                    maxCount
            );
        }
    }

    public void clampSpeakingGoalActiveAndPending(
            int minGoal,
            int maxGoal
    ) {
        this.dailySpeakingGoalMinutes = clamp(
                dailySpeakingGoalMinutes,
                minGoal,
                maxGoal
        );

        if (pendingDailySpeakingGoalMinutes != null) {
            this.pendingDailySpeakingGoalMinutes = clamp(
                    pendingDailySpeakingGoalMinutes,
                    minGoal,
                    maxGoal
            );
        }
    }

    public void clampListeningGoalActiveAndPending(
            int minGoal,
            int maxGoal
    ) {
        this.dailyListeningGoalCount = clamp(
                dailyListeningGoalCount,
                minGoal,
                maxGoal
        );
        if (pendingDailyListeningGoalCount != null) {
            this.pendingDailyListeningGoalCount = clamp(
                    pendingDailyListeningGoalCount,
                    minGoal,
                    maxGoal
            );
        }
    }

    private void applyPendingValues() {
        if (pendingOriginLanguage != null) {
            this.originLanguage = pendingOriginLanguage;
        }
        if (pendingLearningLanguage != null) {
            this.learningLanguage = pendingLearningLanguage;
        }
        if (pendingTimezone != null) {
            this.timezone = pendingTimezone;
        }
        if (pendingDailySentenceCount != null) {
            this.dailySentenceCount = pendingDailySentenceCount;
        }
        if (pendingDailySpeakingGoalMinutes != null) {
            this.dailySpeakingGoalMinutes =
                    pendingDailySpeakingGoalMinutes;
        }
        if (pendingDailyListeningGoalCount != null) {
            this.dailyListeningGoalCount = pendingDailyListeningGoalCount;
        }
    }

    private void clearPendingValues() {
        this.pendingOriginLanguage = null;
        this.pendingLearningLanguage = null;
        this.pendingTimezone = null;
        this.pendingDailySentenceCount = null;
        this.pendingDailySpeakingGoalMinutes = null;
        this.pendingDailyListeningGoalCount = null;
        this.pendingEffectiveDate = null;
    }

    private int clamp(
            int value,
            int minCount,
            int maxCount
    ) {
        return Math.max(minCount, Math.min(maxCount, value));
    }
}
