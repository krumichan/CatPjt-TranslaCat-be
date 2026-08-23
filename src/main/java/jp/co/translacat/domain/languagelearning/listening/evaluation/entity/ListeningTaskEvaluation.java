package jp.co.translacat.domain.languagelearning.listening.evaluation.entity;

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

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.response.entity.ListeningTaskResponse;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_listening_task_evaluation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_listening_evaluation_response_version",
                columnNames = {"task_response_id", "evaluation_version"}
        ),
        indexes = @Index(
                name = "idx_ll_listening_evaluation_task_date",
                columnList = "task_type,evaluated_at"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListeningTaskEvaluation extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_response_id", nullable = false, updatable = false)
    private ListeningTaskResponse taskResponse;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 40)
    private ListeningTaskType taskType;

    private Double score;
    private Double confidence;

    @Column(nullable = false)
    private boolean evaluable;

    @Lob
    @Column(name = "metric_scores", nullable = false, columnDefinition = "TEXT")
    private String metricScoresJson;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String summaryJson;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String strengthsJson;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String improvementsJson;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String evidenceJson;

    @Lob
    @Column(name = "recommended_answers", nullable = false, columnDefinition = "TEXT")
    private String recommendedAnswersJson;

    @Lob
    @Column(name = "profile_signals", nullable = false, columnDefinition = "TEXT")
    private String profileSignalsJson;

    @Lob
    @Column(name = "provider_snapshot", nullable = false, columnDefinition = "TEXT")
    private String providerSnapshotJson;

    @Column(name = "evaluation_version", nullable = false, length = 100)
    private String evaluationVersion;

    @Column(name = "profile_policy_version", nullable = false, length = 100)
    private String profilePolicyVersion;

    @Column(name = "reason_code", length = 100)
    private String reasonCode;

    @Column(name = "evaluated_at", nullable = false)
    private LocalDateTime evaluatedAt;

    private ListeningTaskEvaluation(
            ListeningTaskResponse taskResponse,
            ListeningTaskType taskType,
            Double score,
            Double confidence,
            boolean evaluable,
            String metricScoresJson,
            String summaryJson,
            String strengthsJson,
            String improvementsJson,
            String evidenceJson,
            String recommendedAnswersJson,
            String profileSignalsJson,
            String providerSnapshotJson,
            String evaluationVersion,
            String profilePolicyVersion,
            String reasonCode,
            LocalDateTime evaluatedAt
    ) {
        this.taskResponse = taskResponse;
        this.taskType = taskType;
        this.score = score;
        this.confidence = confidence;
        this.evaluable = evaluable;
        this.metricScoresJson = json(metricScoresJson, "[]");
        this.summaryJson = json(summaryJson, "{}");
        this.strengthsJson = json(strengthsJson, "[]");
        this.improvementsJson = json(improvementsJson, "[]");
        this.evidenceJson = json(evidenceJson, "[]");
        this.recommendedAnswersJson = json(recommendedAnswersJson, "[]");
        this.profileSignalsJson = json(profileSignalsJson, "[]");
        this.providerSnapshotJson = json(providerSnapshotJson, "{}");
        this.evaluationVersion = evaluationVersion;
        this.profilePolicyVersion = profilePolicyVersion;
        this.reasonCode = reasonCode;
        this.evaluatedAt = evaluatedAt;
    }

    public static ListeningTaskEvaluation create(
            ListeningTaskResponse taskResponse,
            ListeningTaskType taskType,
            Double score,
            Double confidence,
            boolean evaluable,
            String metricScoresJson,
            String summaryJson,
            String strengthsJson,
            String improvementsJson,
            String evidenceJson,
            String recommendedAnswersJson,
            String profileSignalsJson,
            String providerSnapshotJson,
            String evaluationVersion,
            String profilePolicyVersion,
            String reasonCode,
            LocalDateTime evaluatedAt
    ) {
        if (!evaluable && score != null) {
            throw new IllegalArgumentException(
                    "평가 불가 결과에는 Score를 저장할 수 없습니다."
            );
        }
        return new ListeningTaskEvaluation(
                taskResponse,
                taskType,
                score,
                confidence,
                evaluable,
                metricScoresJson,
                summaryJson,
                strengthsJson,
                improvementsJson,
                evidenceJson,
                recommendedAnswersJson,
                profileSignalsJson,
                providerSnapshotJson,
                evaluationVersion,
                profilePolicyVersion,
                reasonCode,
                evaluatedAt
        );
    }

    private static String json(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
