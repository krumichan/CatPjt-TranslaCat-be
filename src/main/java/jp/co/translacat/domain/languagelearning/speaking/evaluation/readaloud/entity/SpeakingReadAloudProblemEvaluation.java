package jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_speaking_read_aloud_problem_evaluation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_speaking_ra_eval_session_problem",
                columnNames = {"session_id", "problem_index"}
        ),
        indexes = @Index(
                name = "idx_ll_speaking_ra_eval_session",
                columnList = "session_id,problem_index"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpeakingReadAloudProblemEvaluation extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private SpeakingSession session;

    @Column(name = "problem_index", nullable = false)
    private int problemIndex;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "manual_retry_count", nullable = false)
    private int manualRetryCount;

    @Column(name = "manual_retry_limit", nullable = false)
    private int manualRetryLimit = 1;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "evaluation_confidence")
    private Double evaluationConfidence;

    @Lob
    @Column(name = "metrics_json", nullable = false, columnDefinition = "TEXT")
    private String metricsJson = "[]";

    @Lob
    @Column(name = "strengths_json", nullable = false, columnDefinition = "TEXT")
    private String strengthsJson = "[]";

    @Lob
    @Column(name = "improvements_json", nullable = false, columnDefinition = "TEXT")
    private String improvementsJson = "[]";

    @Lob
    @Column(name = "pronunciation_practice_json", nullable = false, columnDefinition = "TEXT")
    private String pronunciationPracticeJson = "[]";

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;

    private SpeakingReadAloudProblemEvaluation(
            SpeakingSession session,
            int problemIndex,
            int attemptCount
    ) {
        this.session = session;
        this.problemIndex = problemIndex;
        this.attemptCount = attemptCount;
        this.status = "PENDING";
        this.submittedAt = LocalDateTime.now();
    }

    public static SpeakingReadAloudProblemEvaluation pending(
            SpeakingSession session,
            int problemIndex,
            int attemptCount
    ) {
        return new SpeakingReadAloudProblemEvaluation(
                session,
                problemIndex,
                attemptCount
        );
    }

    public void configureRetryLimit(int limit) {
        this.manualRetryLimit = Math.max(0, limit);
    }

    public void acceptRetry(int manualRetryCount) {
        this.manualRetryCount = manualRetryCount;
        markPending();
    }

    public void markPending() {
        this.status = "PENDING";
        this.errorMessage = null;
        this.evaluatedAt = null;
    }

    public void markSkipped() {
        this.status = "NOT_REQUESTED";
        this.overallScore = null;
        this.evaluationConfidence = null;
        this.evaluatedAt = LocalDateTime.now();
    }

    public void markEvaluating() {
        this.status = "EVALUATING";
        this.errorMessage = null;
    }

    public void markEvaluated(
            String status,
            Integer overallScore,
            Double evaluationConfidence,
            String metricsJson,
            String strengthsJson,
            String improvementsJson,
            String pronunciationPracticeJson
    ) {
        this.status = status == null || status.isBlank()
                ? "EVALUATED"
                : status;
        this.overallScore = overallScore;
        this.evaluationConfidence = evaluationConfidence;
        this.metricsJson = metricsJson == null ? "[]" : metricsJson;
        this.strengthsJson = strengthsJson == null ? "[]" : strengthsJson;
        this.improvementsJson = improvementsJson == null ? "[]" : improvementsJson;
        this.pronunciationPracticeJson = pronunciationPracticeJson == null
                ? "[]" : pronunciationPracticeJson;
        this.errorMessage = null;
        this.evaluatedAt = LocalDateTime.now();
    }

    public void markFailed(String message) {
        this.status = "FAILED";
        this.errorMessage = message == null
                ? "Read Aloud 문제 평가에 실패했습니다."
                : message.length() <= 1000 ? message : message.substring(0, 1000);
        this.evaluatedAt = LocalDateTime.now();
    }
}
