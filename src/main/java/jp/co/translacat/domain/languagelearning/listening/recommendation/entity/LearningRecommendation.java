package jp.co.translacat.domain.languagelearning.listening.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningProfileMetric;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningRecommendationStatus;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_recommendation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_recommendation_user_language_metric_version",
                columnNames = {
                        "user_id",
                        "learning_language",
                        "target_metric",
                        "calculation_version"
                }
        ),
        indexes = @Index(
                name = "idx_ll_recommendation_user_status",
                columnList = "user_id,status,priority"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningRecommendation extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "learning_language", nullable = false, length = 20)
    private String learningLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_metric", nullable = false, length = 40)
    private ListeningProfileMetric targetMetric;

    @Column(name = "recommended_activity", nullable = false, length = 40)
    private String recommendedActivity;

    @Column(name = "recommended_task", nullable = false, length = 80)
    private String recommendedTask;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "cta_label", nullable = false, length = 80)
    private String ctaLabel;

    @Lob
    @Column(name = "evidence_references", nullable = false, columnDefinition = "TEXT")
    private String evidenceReferencesJson;

    @Column(nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ListeningRecommendationStatus status;

    @Column(name = "dismissed_at")
    private LocalDateTime dismissedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "calculation_version", nullable = false, length = 100)
    private String calculationVersion;

    @Column(name = "explanation_version", length = 100)
    private String explanationVersion;

    private LearningRecommendation(
            User user,
            String learningLanguage,
            ListeningProfileMetric targetMetric,
            String recommendedActivity,
            String recommendedTask,
            String reason,
            String evidenceReferencesJson,
            int priority,
            LocalDateTime expiresAt,
            String calculationVersion
    ) {
        this.user = user;
        this.learningLanguage = learningLanguage;
        this.targetMetric = targetMetric;
        this.recommendedActivity = recommendedActivity;
        this.recommendedTask = recommendedTask;
        this.reason = reason;
        this.ctaLabel = "학습 시작";
        this.evidenceReferencesJson = evidenceReferencesJson == null
                ? "[]"
                : evidenceReferencesJson;
        this.priority = priority;
        this.status = ListeningRecommendationStatus.ACTIVE;
        this.expiresAt = expiresAt;
        this.calculationVersion = calculationVersion;
    }

    public static LearningRecommendation create(
            User user,
            String learningLanguage,
            ListeningProfileMetric targetMetric,
            String recommendedActivity,
            String recommendedTask,
            String reason,
            String evidenceReferencesJson,
            int priority,
            LocalDateTime expiresAt,
            String calculationVersion
    ) {
        return new LearningRecommendation(
                user,
                learningLanguage,
                targetMetric,
                recommendedActivity,
                recommendedTask,
                reason,
                evidenceReferencesJson,
                priority,
                expiresAt,
                calculationVersion
        );
    }

    public void refresh(
            String reason,
            String evidenceReferencesJson,
            int priority,
            LocalDateTime expiresAt
    ) {
        if (status == ListeningRecommendationStatus.DISMISSED) {
            return;
        }
        if (explanationVersion == null) {
            this.reason = reason;
        }
        this.evidenceReferencesJson = evidenceReferencesJson;
        this.priority = priority;
        this.expiresAt = expiresAt;
        this.status = ListeningRecommendationStatus.ACTIVE;
    }

    public void dismiss(LocalDateTime now) {
        status = ListeningRecommendationStatus.DISMISSED;
        dismissedAt = now;
    }

    public void applyExplanation(
            String explanation,
            String ctaLabel,
            String version
    ) {
        if (status != ListeningRecommendationStatus.ACTIVE
                || explanation == null || explanation.isBlank()
                || ctaLabel == null || ctaLabel.isBlank()
                || version == null || version.isBlank()) {
            return;
        }
        reason = explanation;
        this.ctaLabel = ctaLabel;
        explanationVersion = version;
    }

    public void resolve() {
        if (status == ListeningRecommendationStatus.ACTIVE) {
            status = ListeningRecommendationStatus.RESOLVED;
        }
    }

    public void expire(LocalDateTime now) {
        if (status == ListeningRecommendationStatus.ACTIVE
                && expiresAt.isBefore(now)) {
            status = ListeningRecommendationStatus.EXPIRED;
        }
    }
}
