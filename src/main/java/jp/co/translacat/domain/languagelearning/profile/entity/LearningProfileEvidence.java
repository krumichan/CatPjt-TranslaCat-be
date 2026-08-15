package jp.co.translacat.domain.languagelearning.profile.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingMetricType;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_profile_evidence",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_profile_evidence_source_pattern",
                columnNames = {
                        "user_id",
                        "source",
                        "pattern_key",
                        "direction"
                }
        ),
        indexes = @Index(
                name = "idx_ll_profile_evidence_user_source",
                columnList = "user_id,source"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningProfileEvidence extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LearningSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", length = 40)
    private SpeakingMetricType metricType;

    @Column(name = "pattern_key", nullable = false, length = 300)
    private String patternKey;

    @Column(nullable = false, length = 30)
    private String direction;

    @Column(nullable = false)
    private int evidenceCount;

    @Column(nullable = false)
    private double weightedEvidence;

    @Column(nullable = false)
    private double averageConfidence;

    @Column(name = "recommended_focus", length = 1000)
    private String recommendedFocus;

    @Column(nullable = false)
    private LocalDateTime lastSeenAt;

    private LearningProfileEvidence(
            User user,
            LearningSource source,
            SpeakingMetricType metricType,
            String patternKey,
            String direction,
            double confidence,
            double activityWeight,
            String recommendedFocus
    ) {
        this.user = user;
        this.source = source;
        this.metricType = metricType;
        this.patternKey = patternKey;
        this.direction = direction;
        this.evidenceCount = 1;
        this.weightedEvidence = activityWeight;
        this.averageConfidence = confidence;
        this.recommendedFocus = recommendedFocus;
        this.lastSeenAt = LocalDateTime.now();
    }

    public static LearningProfileEvidence create(
            User user,
            LearningSource source,
            SpeakingMetricType metricType,
            String patternKey,
            String direction,
            double confidence,
            double activityWeight,
            String recommendedFocus
    ) {
        return new LearningProfileEvidence(
                user,
                source,
                metricType,
                patternKey,
                direction,
                confidence,
                activityWeight,
                recommendedFocus
        );
    }

    public void touch(
            SpeakingMetricType metricType,
            double confidence,
            double activityWeight,
            String recommendedFocus
    ) {
        this.metricType = metricType == null ? this.metricType : metricType;
        this.averageConfidence = (
                this.averageConfidence * this.evidenceCount + confidence
        ) / (this.evidenceCount + 1);
        this.evidenceCount++;
        this.weightedEvidence += Math.max(0, activityWeight);
        if (recommendedFocus != null && !recommendedFocus.isBlank()) {
            this.recommendedFocus = recommendedFocus;
        }
        this.lastSeenAt = LocalDateTime.now();
    }
}
