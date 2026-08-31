package jp.co.translacat.domain.languagelearning.listening.profile.entity;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningAssistanceLevel;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningProfileMetric;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "language_learning_listening_metric_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_listening_history_evaluation_metric",
                columnNames = {"reference_evaluation_id", "metric_type"}
        ),
        indexes = {
                @Index(
                        name = "idx_ll_listening_history_profile",
                        columnList = "user_id,learning_language,metric_type,created_at"
                ),
                @Index(
                        name = "idx_ll_listening_history_source",
                        columnList = "user_id,learning_language,task_type,metric_type"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListeningMetricHistory extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "learning_language", nullable = false, length = 20)
    private String learningLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", length = 40)
    private ListeningTaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 40)
    private ListeningProfileMetric metricType;

    @Column(name = "raw_score", nullable = false)
    private double rawScore;

    @Column(nullable = false)
    private double confidence;

    @Column(name = "recency_weight", nullable = false)
    private double recencyWeight;

    @Column(name = "assistance_weight", nullable = false)
    private double assistanceWeight;

    @Column(name = "evidence_weight", nullable = false)
    private double evidenceWeight;

    @Column(name = "final_weight", nullable = false)
    private double finalWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "assistance_level", nullable = false, length = 20)
    private ListeningAssistanceLevel assistanceLevel;

    @Column(name = "reference_activity_id", nullable = false, length = 100)
    private String referenceActivityId;

    @Column(name = "reference_evaluation_id", nullable = false, length = 100)
    private String referenceEvaluationId;

    @Column(nullable = false)
    private boolean official;

    @Column(nullable = false)
    private boolean practice;

    @Column(name = "profile_applied", nullable = false)
    private boolean profileApplied;

    @Column(name = "evaluation_version", nullable = false, length = 100)
    private String evaluationVersion;

    @Column(name = "profile_policy_version", nullable = false, length = 100)
    private String profilePolicyVersion;

    private ListeningMetricHistory(
            User user,
            String learningLanguage,
            ListeningTaskType taskType,
            ListeningProfileMetric metricType,
            double rawScore,
            double confidence,
            double recencyWeight,
            double assistanceWeight,
            double evidenceWeight,
            double finalWeight,
            ListeningAssistanceLevel assistanceLevel,
            String referenceActivityId,
            String referenceEvaluationId,
            boolean official,
            boolean practice,
            boolean profileApplied,
            String evaluationVersion,
            String profilePolicyVersion
    ) {
        this.user = user;
        this.learningLanguage = learningLanguage;
        this.taskType = taskType;
        this.metricType = metricType;
        this.rawScore = rawScore;
        this.confidence = confidence;
        this.recencyWeight = recencyWeight;
        this.assistanceWeight = assistanceWeight;
        this.evidenceWeight = evidenceWeight;
        this.finalWeight = finalWeight;
        this.assistanceLevel = assistanceLevel;
        this.referenceActivityId = referenceActivityId;
        this.referenceEvaluationId = referenceEvaluationId;
        this.official = official;
        this.practice = practice;
        this.profileApplied = profileApplied;
        this.evaluationVersion = evaluationVersion;
        this.profilePolicyVersion = profilePolicyVersion;
    }

    public static ListeningMetricHistory create(
            User user,
            String learningLanguage,
            ListeningTaskType taskType,
            ListeningProfileMetric metricType,
            double rawScore,
            double confidence,
            double recencyWeight,
            double assistanceWeight,
            double evidenceWeight,
            double finalWeight,
            ListeningAssistanceLevel assistanceLevel,
            String referenceActivityId,
            String referenceEvaluationId,
            boolean official,
            boolean practice,
            boolean profileApplied,
            String evaluationVersion,
            String profilePolicyVersion
    ) {
        return new ListeningMetricHistory(
                user,
                learningLanguage,
                taskType,
                metricType,
                rawScore,
                confidence,
                recencyWeight,
                assistanceWeight,
                evidenceWeight,
                finalWeight,
                assistanceLevel,
                referenceActivityId,
                referenceEvaluationId,
                official,
                practice,
                profileApplied,
                evaluationVersion,
                profilePolicyVersion
        );
    }

    public void updateRecency(double recencyWeight, double finalWeight) {
        this.recencyWeight = recencyWeight;
        this.finalWeight = finalWeight;
    }
}
