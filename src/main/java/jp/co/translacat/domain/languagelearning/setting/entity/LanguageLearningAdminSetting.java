package jp.co.translacat.domain.languagelearning.setting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "language_learning_admin_setting")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LanguageLearningAdminSetting extends BaseAuditable {

    public static final String DEFAULT_ID = "DEFAULT";

    public static final int DEFAULT_DAILY_SENTENCE_COUNT = 5;
    public static final int DEFAULT_MIN_DAILY_SENTENCE_COUNT = 1;
    public static final int DEFAULT_MAX_DAILY_SENTENCE_COUNT = 20;
    public static final int DEFAULT_DAILY_KEYWORD_MAX_COUNT = 5;
    public static final int DEFAULT_REVIEW_AVAILABLE_DAYS = 7;
    public static final int DEFAULT_LEVEL_RECHECK_RECOMMENDATION_DAYS = 30;

    public static final int DEFAULT_DAILY_SPEAKING_GOAL_MINUTES = 5;
    public static final int DEFAULT_MIN_DAILY_SPEAKING_GOAL_MINUTES = 3;
    public static final int DEFAULT_MAX_DAILY_SPEAKING_GOAL_MINUTES = 20;
    public static final int DEFAULT_DAILY_SPEAKING_HARD_LIMIT_MINUTES = 30;
    public static final int DEFAULT_DAILY_SPEAKING_SESSION_LIMIT = 5;
    public static final int DEFAULT_MAX_SESSION_MINUTES = 10;
    public static final int DEFAULT_MAX_TURNS_PER_SESSION = 20;
    public static final double DEFAULT_MIN_VALID_AUDIO_SECONDS = 1.0;
    public static final int DEFAULT_MAX_TURN_AUDIO_SECONDS = 60;
    public static final long DEFAULT_MAX_AUDIO_FILE_BYTES = 10L * 1024L * 1024L;
    public static final int DEFAULT_RAW_AUDIO_RETENTION_DAYS = 7;
    public static final int DEFAULT_REPORTED_AUDIO_RETENTION_DAYS = 30;
    public static final int DEFAULT_ACTIVE_SESSION_RESUME_HOURS = 2;
    public static final int DEFAULT_AUTOMATIC_RETRY_LIMIT_PER_STAGE = 2;
    public static final int DEFAULT_MANUAL_RETRY_LIMIT_PER_STAGE = 1;
    public static final int DEFAULT_STT_TIMEOUT_SECONDS = 30;
    public static final int DEFAULT_TTS_TIMEOUT_SECONDS = 30;
    public static final int DEFAULT_EVALUATION_TIMEOUT_SECONDS = 60;

    @Id
    @Column(length = 30)
    private String id;

    @Column(nullable = false)
    private int defaultDailySentenceCount;

    @Column(nullable = false)
    private int minDailySentenceCount;

    @Column(nullable = false)
    private int maxDailySentenceCount;

    @Column(nullable = false)
    private int dailyKeywordMaxCount;

    @Column(nullable = false)
    private int reviewAvailableDays;

    @Column(nullable = false)
    private int levelRecheckRecommendationDays;

    @Column(nullable = false)
    private boolean adaptiveWritingEnabled;

    @Column(nullable = false)
    private boolean aiEvaluationEnabled;

    @Column(nullable = false)
    private boolean speakingEnabled;

    @Column(nullable = false)
    private boolean speakingEvaluationEnabled;

    @Column(nullable = false)
    private int defaultDailySpeakingGoalMinutes;

    @Column(nullable = false)
    private int minDailySpeakingGoalMinutes;

    @Column(nullable = false)
    private int maxDailySpeakingGoalMinutes;

    @Column(nullable = false)
    private int dailySpeakingHardLimitMinutes;

    @Column(nullable = false)
    private int dailySpeakingSessionLimit;

    @Column(nullable = false)
    private int maxSessionMinutes;

    @Column(nullable = false)
    private int maxTurnsPerSession;

    @Column(nullable = false)
    private double minValidAudioSeconds;

    @Column(nullable = false)
    private int maxTurnAudioSeconds;

    @Column(nullable = false)
    private long maxAudioFileBytes;

    @Column(nullable = false)
    private int rawAudioRetentionDays;

    @Column(nullable = false)
    private int reportedAudioRetentionDays;

    @Column(nullable = false)
    private int activeSessionResumeHours;

    @Column(nullable = false)
    private int automaticRetryLimitPerStage;

    @Column(nullable = false)
    private int manualRetryLimitPerStage;

    @Column(nullable = false)
    private int sttTimeoutSeconds;

    @Column(nullable = false)
    private int ttsTimeoutSeconds;

    @Column(nullable = false)
    private int evaluationTimeoutSeconds;

    private LanguageLearningAdminSetting(String id) {
        this.id = id;
        this.defaultDailySentenceCount = DEFAULT_DAILY_SENTENCE_COUNT;
        this.minDailySentenceCount = DEFAULT_MIN_DAILY_SENTENCE_COUNT;
        this.maxDailySentenceCount = DEFAULT_MAX_DAILY_SENTENCE_COUNT;
        this.dailyKeywordMaxCount = DEFAULT_DAILY_KEYWORD_MAX_COUNT;
        this.reviewAvailableDays = DEFAULT_REVIEW_AVAILABLE_DAYS;
        this.levelRecheckRecommendationDays =
                DEFAULT_LEVEL_RECHECK_RECOMMENDATION_DAYS;
        this.adaptiveWritingEnabled = true;
        this.aiEvaluationEnabled = true;

        this.speakingEnabled = true;
        this.speakingEvaluationEnabled = true;
        this.defaultDailySpeakingGoalMinutes =
                DEFAULT_DAILY_SPEAKING_GOAL_MINUTES;
        this.minDailySpeakingGoalMinutes =
                DEFAULT_MIN_DAILY_SPEAKING_GOAL_MINUTES;
        this.maxDailySpeakingGoalMinutes =
                DEFAULT_MAX_DAILY_SPEAKING_GOAL_MINUTES;
        this.dailySpeakingHardLimitMinutes =
                DEFAULT_DAILY_SPEAKING_HARD_LIMIT_MINUTES;
        this.dailySpeakingSessionLimit =
                DEFAULT_DAILY_SPEAKING_SESSION_LIMIT;
        this.maxSessionMinutes = DEFAULT_MAX_SESSION_MINUTES;
        this.maxTurnsPerSession = DEFAULT_MAX_TURNS_PER_SESSION;
        this.minValidAudioSeconds = DEFAULT_MIN_VALID_AUDIO_SECONDS;
        this.maxTurnAudioSeconds = DEFAULT_MAX_TURN_AUDIO_SECONDS;
        this.maxAudioFileBytes = DEFAULT_MAX_AUDIO_FILE_BYTES;
        this.rawAudioRetentionDays = DEFAULT_RAW_AUDIO_RETENTION_DAYS;
        this.reportedAudioRetentionDays =
                DEFAULT_REPORTED_AUDIO_RETENTION_DAYS;
        this.activeSessionResumeHours =
                DEFAULT_ACTIVE_SESSION_RESUME_HOURS;
        this.automaticRetryLimitPerStage =
                DEFAULT_AUTOMATIC_RETRY_LIMIT_PER_STAGE;
        this.manualRetryLimitPerStage =
                DEFAULT_MANUAL_RETRY_LIMIT_PER_STAGE;
        this.sttTimeoutSeconds = DEFAULT_STT_TIMEOUT_SECONDS;
        this.ttsTimeoutSeconds = DEFAULT_TTS_TIMEOUT_SECONDS;
        this.evaluationTimeoutSeconds = DEFAULT_EVALUATION_TIMEOUT_SECONDS;
    }

    public static LanguageLearningAdminSetting createDefault() {
        return new LanguageLearningAdminSetting(DEFAULT_ID);
    }

    /**
     * Phase 1 호환용 update.
     */
    public void update(
            Integer defaultCount,
            Integer minCount,
            Integer maxCount,
            Integer keywordMax,
            Integer reviewDays,
            Integer recheckDays,
            Boolean adaptiveWritingEnabled,
            Boolean aiEvaluationEnabled
    ) {
        int nextDefault = defaultCount == null
                ? this.defaultDailySentenceCount
                : defaultCount;
        int nextMin = minCount == null
                ? this.minDailySentenceCount
                : minCount;
        int nextMax = maxCount == null
                ? this.maxDailySentenceCount
                : maxCount;
        int nextKeywordMax = keywordMax == null
                ? this.dailyKeywordMaxCount
                : keywordMax;
        int nextReviewDays = reviewDays == null
                ? this.reviewAvailableDays
                : reviewDays;
        int nextRecheckDays = recheckDays == null
                ? this.levelRecheckRecommendationDays
                : recheckDays;

        validateWriting(
                nextDefault,
                nextMin,
                nextMax,
                nextKeywordMax,
                nextReviewDays,
                nextRecheckDays
        );

        this.defaultDailySentenceCount = nextDefault;
        this.minDailySentenceCount = nextMin;
        this.maxDailySentenceCount = nextMax;
        this.dailyKeywordMaxCount = nextKeywordMax;
        this.reviewAvailableDays = nextReviewDays;
        this.levelRecheckRecommendationDays = nextRecheckDays;

        if (adaptiveWritingEnabled != null) {
            this.adaptiveWritingEnabled = adaptiveWritingEnabled;
        }
        if (aiEvaluationEnabled != null) {
            this.aiEvaluationEnabled = aiEvaluationEnabled;
        }
    }

    public void update(
            Integer defaultCount,
            Integer minCount,
            Integer maxCount,
            Integer keywordMax,
            Integer reviewDays,
            Integer recheckDays,
            Boolean adaptiveWritingEnabled,
            Boolean aiEvaluationEnabled,
            Boolean speakingEnabled,
            Boolean speakingEvaluationEnabled,
            Integer defaultSpeakingGoal,
            Integer minSpeakingGoal,
            Integer maxSpeakingGoal,
            Integer speakingHardLimit,
            Integer speakingSessionLimit,
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
        int nextDefault = defaultCount == null
                ? this.defaultDailySentenceCount
                : defaultCount;
        int nextMin = minCount == null
                ? this.minDailySentenceCount
                : minCount;
        int nextMax = maxCount == null
                ? this.maxDailySentenceCount
                : maxCount;
        int nextKeywordMax = keywordMax == null
                ? this.dailyKeywordMaxCount
                : keywordMax;
        int nextReviewDays = reviewDays == null
                ? this.reviewAvailableDays
                : reviewDays;
        int nextRecheckDays = recheckDays == null
                ? this.levelRecheckRecommendationDays
                : recheckDays;

        int nextDefaultSpeakingGoal = defaultSpeakingGoal == null
                ? this.defaultDailySpeakingGoalMinutes
                : defaultSpeakingGoal;
        int nextMinSpeakingGoal = minSpeakingGoal == null
                ? this.minDailySpeakingGoalMinutes
                : minSpeakingGoal;
        int nextMaxSpeakingGoal = maxSpeakingGoal == null
                ? this.maxDailySpeakingGoalMinutes
                : maxSpeakingGoal;
        int nextSpeakingHardLimit = speakingHardLimit == null
                ? this.dailySpeakingHardLimitMinutes
                : speakingHardLimit;
        int nextSpeakingSessionLimit = speakingSessionLimit == null
                ? this.dailySpeakingSessionLimit
                : speakingSessionLimit;
        int nextMaxSessionMinutes = maxSessionMinutes == null
                ? this.maxSessionMinutes
                : maxSessionMinutes;
        int nextMaxTurns = maxTurnsPerSession == null
                ? this.maxTurnsPerSession
                : maxTurnsPerSession;
        double nextMinAudioSeconds = minValidAudioSeconds == null
                ? this.minValidAudioSeconds
                : minValidAudioSeconds;
        int nextMaxTurnAudioSeconds = maxTurnAudioSeconds == null
                ? this.maxTurnAudioSeconds
                : maxTurnAudioSeconds;
        long nextMaxAudioBytes = maxAudioFileBytes == null
                ? this.maxAudioFileBytes
                : maxAudioFileBytes;
        int nextRawRetention = rawAudioRetentionDays == null
                ? this.rawAudioRetentionDays
                : rawAudioRetentionDays;
        int nextReportedRetention = reportedAudioRetentionDays == null
                ? this.reportedAudioRetentionDays
                : reportedAudioRetentionDays;
        int nextResumeHours = activeSessionResumeHours == null
                ? this.activeSessionResumeHours
                : activeSessionResumeHours;
        int nextAutoRetry = automaticRetryLimitPerStage == null
                ? this.automaticRetryLimitPerStage
                : automaticRetryLimitPerStage;
        int nextManualRetry = manualRetryLimitPerStage == null
                ? this.manualRetryLimitPerStage
                : manualRetryLimitPerStage;
        int nextSttTimeout = sttTimeoutSeconds == null
                ? this.sttTimeoutSeconds
                : sttTimeoutSeconds;
        int nextTtsTimeout = ttsTimeoutSeconds == null
                ? this.ttsTimeoutSeconds
                : ttsTimeoutSeconds;
        int nextEvaluationTimeout = evaluationTimeoutSeconds == null
                ? this.evaluationTimeoutSeconds
                : evaluationTimeoutSeconds;

        validateWriting(
                nextDefault,
                nextMin,
                nextMax,
                nextKeywordMax,
                nextReviewDays,
                nextRecheckDays
        );
        validateSpeaking(
                nextDefaultSpeakingGoal,
                nextMinSpeakingGoal,
                nextMaxSpeakingGoal,
                nextSpeakingHardLimit,
                nextSpeakingSessionLimit,
                nextMaxSessionMinutes,
                nextMaxTurns,
                nextMinAudioSeconds,
                nextMaxTurnAudioSeconds,
                nextMaxAudioBytes,
                nextRawRetention,
                nextReportedRetention,
                nextResumeHours,
                nextAutoRetry,
                nextManualRetry,
                nextSttTimeout,
                nextTtsTimeout,
                nextEvaluationTimeout
        );

        this.defaultDailySentenceCount = nextDefault;
        this.minDailySentenceCount = nextMin;
        this.maxDailySentenceCount = nextMax;
        this.dailyKeywordMaxCount = nextKeywordMax;
        this.reviewAvailableDays = nextReviewDays;
        this.levelRecheckRecommendationDays = nextRecheckDays;

        this.defaultDailySpeakingGoalMinutes = nextDefaultSpeakingGoal;
        this.minDailySpeakingGoalMinutes = nextMinSpeakingGoal;
        this.maxDailySpeakingGoalMinutes = nextMaxSpeakingGoal;
        this.dailySpeakingHardLimitMinutes = nextSpeakingHardLimit;
        this.dailySpeakingSessionLimit = nextSpeakingSessionLimit;
        this.maxSessionMinutes = nextMaxSessionMinutes;
        this.maxTurnsPerSession = nextMaxTurns;
        this.minValidAudioSeconds = nextMinAudioSeconds;
        this.maxTurnAudioSeconds = nextMaxTurnAudioSeconds;
        this.maxAudioFileBytes = nextMaxAudioBytes;
        this.rawAudioRetentionDays = nextRawRetention;
        this.reportedAudioRetentionDays = nextReportedRetention;
        this.activeSessionResumeHours = nextResumeHours;
        this.automaticRetryLimitPerStage = nextAutoRetry;
        this.manualRetryLimitPerStage = nextManualRetry;
        this.sttTimeoutSeconds = nextSttTimeout;
        this.ttsTimeoutSeconds = nextTtsTimeout;
        this.evaluationTimeoutSeconds = nextEvaluationTimeout;

        if (adaptiveWritingEnabled != null) {
            this.adaptiveWritingEnabled = adaptiveWritingEnabled;
        }
        if (aiEvaluationEnabled != null) {
            this.aiEvaluationEnabled = aiEvaluationEnabled;
        }
        if (speakingEnabled != null) {
            this.speakingEnabled = speakingEnabled;
        }
        if (speakingEvaluationEnabled != null) {
            this.speakingEvaluationEnabled = speakingEvaluationEnabled;
        }
    }

    public int clampDailySentenceCount(int value) {
        return Math.max(
                minDailySentenceCount,
                Math.min(maxDailySentenceCount, value)
        );
    }

    public int clampDailySpeakingGoalMinutes(int value) {
        return Math.max(
                minDailySpeakingGoalMinutes,
                Math.min(maxDailySpeakingGoalMinutes, value)
        );
    }

    private static void validateWriting(
            int defaultCount,
            int minCount,
            int maxCount,
            int keywordMax,
            int reviewDays,
            int recheckDays
    ) {
        boolean invalidSentenceRange = minCount < 1
                || maxCount < minCount
                || maxCount > 100
                || defaultCount < minCount
                || defaultCount > maxCount;
        boolean invalidKeywordMax = keywordMax < 0 || keywordMax > 20;
        boolean invalidReviewDays = reviewDays < 1 || reviewDays > 365;
        boolean invalidRecheckDays = recheckDays < 1 || recheckDays > 3650;

        if (invalidSentenceRange
                || invalidKeywordMax
                || invalidReviewDays
                || invalidRecheckDays) {
            invalidSetting();
        }
    }

    private static void validateSpeaking(
            int defaultGoal,
            int minGoal,
            int maxGoal,
            int hardLimit,
            int sessionLimit,
            int sessionMinutes,
            int maxTurns,
            double minAudioSeconds,
            int maxAudioSeconds,
            long maxAudioBytes,
            int rawRetention,
            int reportedRetention,
            int resumeHours,
            int autoRetry,
            int manualRetry,
            int sttTimeout,
            int ttsTimeout,
            int evaluationTimeout
    ) {
        boolean invalidGoal = minGoal < 1
                || maxGoal < minGoal
                || defaultGoal < minGoal
                || defaultGoal > maxGoal
                || hardLimit < maxGoal
                || hardLimit > 240;
        boolean invalidSession = sessionLimit < 1
                || sessionLimit > 100
                || sessionMinutes < 1
                || sessionMinutes > 10
                || maxTurns < 1
                || maxTurns > 20;
        boolean invalidAudio = minAudioSeconds < 0.1
                || minAudioSeconds > 10
                || maxAudioSeconds < minAudioSeconds
                || maxAudioSeconds > 60
                || maxAudioBytes < 1024
                || maxAudioBytes > 10L * 1024L * 1024L;
        boolean invalidRetention = rawRetention < 1
                || rawRetention > 365
                || reportedRetention < rawRetention
                || reportedRetention > 365;
        boolean invalidRetry = autoRetry < 0
                || autoRetry > 2
                || manualRetry < 0
                || manualRetry > 1;
        boolean invalidTimeout = sttTimeout < 1
                || ttsTimeout < 1
                || evaluationTimeout < 1
                || resumeHours < 1
                || resumeHours > 24;

        if (invalidGoal
                || invalidSession
                || invalidAudio
                || invalidRetention
                || invalidRetry
                || invalidTimeout) {
            invalidSetting();
        }
    }

    private static void invalidSetting() {
        throw new BusinessException(
                "언어학습 관리자 설정값이 유효하지 않습니다.",
                LanguageLearningErrorCode.SETTING_INVALID
        );
    }
}
