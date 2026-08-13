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
    }

    public static LanguageLearningAdminSetting createDefault() {
        return new LanguageLearningAdminSetting(DEFAULT_ID);
    }

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

        validate(
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

    public int clampDailySentenceCount(int value) {
        return Math.max(
                minDailySentenceCount,
                Math.min(maxDailySentenceCount, value)
        );
    }

    private static void validate(
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
            throw new BusinessException(
                    "언어학습 관리자 설정값이 유효하지 않습니다.",
                    LanguageLearningErrorCode.SETTING_INVALID
            );
        }
    }
}
