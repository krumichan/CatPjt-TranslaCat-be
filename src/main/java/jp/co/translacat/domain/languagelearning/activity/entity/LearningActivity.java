package jp.co.translacat.domain.languagelearning.activity.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.common.enums.LearningActivityStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_activity",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_activity_source_reference",
                columnNames = {"source", "reference_id"}
        ),
        indexes = {
                @Index(
                        name = "idx_ll_activity_user_date",
                        columnList = "user_id,learning_date"
                ),
                @Index(
                        name = "idx_ll_activity_user_source",
                        columnList = "user_id,source"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningActivity extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LearningSource source;

    @Column(name = "reference_id", nullable = false, length = 100)
    private String referenceId;

    @Column(name = "learning_date", nullable = false)
    private LocalDate learningDate;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false)
    private long durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private LearningActivityStatus status;

    private Double overallScore;
    private Double evaluationConfidence;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String metadataJson = "{}";

    private LearningActivity(
            User user,
            LearningSource source,
            String referenceId,
            LocalDate learningDate,
            String title,
            long durationSeconds,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            LearningActivityStatus status
    ) {
        this.user = user;
        this.source = source;
        this.referenceId = referenceId;
        this.learningDate = learningDate;
        this.title = title;
        this.durationSeconds = durationSeconds;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.status = status;
    }

    public static LearningActivity create(
            User user,
            LearningSource source,
            String referenceId,
            LocalDate learningDate,
            String title,
            long durationSeconds,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            LearningActivityStatus status
    ) {
        return new LearningActivity(
                user,
                source,
                referenceId,
                learningDate,
                title,
                durationSeconds,
                startedAt,
                completedAt,
                status
        );
    }

    public void markEvaluating() {
        this.status = LearningActivityStatus.EVALUATING;
    }

    public void markEvaluated(
            double overallScore,
            double evaluationConfidence
    ) {
        this.overallScore = overallScore;
        this.evaluationConfidence = evaluationConfidence;
        this.status = LearningActivityStatus.EVALUATED;
    }

    public void markInsufficientEvidence(Double evaluationConfidence) {
        this.overallScore = null;
        this.evaluationConfidence = evaluationConfidence;
        this.status = LearningActivityStatus.INSUFFICIENT_EVIDENCE;
    }

    public void markEvaluationFailed() {
        this.status = LearningActivityStatus.EVALUATION_FAILED;
    }

    public void updateDuration(long durationSeconds) {
        this.durationSeconds = Math.max(0, durationSeconds);
    }

    public void updateMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson == null ? "{}" : metadataJson;
    }
}
