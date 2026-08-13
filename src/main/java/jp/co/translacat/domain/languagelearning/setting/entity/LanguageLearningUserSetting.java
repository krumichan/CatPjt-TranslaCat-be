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

    @Column(length = 20)
    private String pendingOriginLanguage;

    @Column(length = 20)
    private String pendingLearningLanguage;

    @Column(length = 60)
    private String pendingTimezone;

    private Integer pendingDailySentenceCount;
    private LocalDate pendingEffectiveDate;

    private LanguageLearningUserSetting(
            User user,
            int defaultDailySentenceCount
    ) {
        this.user = user;
        this.timezone = DEFAULT_TIMEZONE;
        this.dailySentenceCount = defaultDailySentenceCount;
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
    }

    public void scheduleUpdate(
            String originLanguage,
            String learningLanguage,
            String timezone,
            Integer dailySentenceCount,
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

        this.pendingEffectiveDate = effectiveDate;
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
    }

    private void clearPendingValues() {
        this.pendingOriginLanguage = null;
        this.pendingLearningLanguage = null;
        this.pendingTimezone = null;
        this.pendingDailySentenceCount = null;
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
