package jp.co.translacat.domain.languagelearning.level.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_level_test_session",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_level_session_user_key",
                columnNames = {"user_id", "idempotency_key"}
        ),
        indexes = {
                @Index(
                        name = "idx_ll_level_session_user_status",
                        columnList = "user_id,status"
                ),
                @Index(
                        name = "idx_ll_level_session_user_completed",
                        columnList = "user_id,completed_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LevelTestSession extends BaseAuditable {

    public static final int DEFAULT_TOTAL_QUESTIONS = 20;
    public static final String DEFAULT_GENERATION_POLICY_VERSION =
            "LEVEL_TEST_MULTI_SKILL";
    public static final String DEFAULT_SCORING_POLICY_VERSION =
            "LEVEL_TEST_SCORING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LevelTestSessionType sessionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LevelTestSessionStatus status;

    @Column(name = "origin_language", length = 20)
    private String originLanguage;

    @Column(name = "learning_language", length = 20)
    private String learningLanguage;

    @Column(nullable = false)
    private int totalQuestions;

    @Column(name = "current_question_number")
    private Integer currentQuestionNumber;

    @Column(name = "current_complexity_band")
    private Integer currentComplexityBand;

    @Column(nullable = false, length = 50)
    private String levelPolicyVersion = DEFAULT_SCORING_POLICY_VERSION;

    private Double baseLevelScore;

    @Column(name = "proficiency_band", length = 40)
    private String proficiencyBand;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    private LocalDateTime completedAt;

    @Column(name = "generation_policy_version", length = 100)
    private String generationPolicyVersion;

    @Column(name = "idempotency_key", length = 200)
    private String idempotencyKey;

    private LevelTestSession(
            User user,
            LevelTestSessionType sessionType,
            String originLanguage,
            String learningLanguage,
            int initialComplexityBand,
            String idempotencyKey,
            LocalDateTime startedAt
    ) {
        this.user = user;
        this.sessionType = sessionType;
        this.status = LevelTestSessionStatus.IN_PROGRESS;
        this.originLanguage = originLanguage;
        this.learningLanguage = learningLanguage;
        this.totalQuestions = DEFAULT_TOTAL_QUESTIONS;
        this.currentQuestionNumber = 1;
        this.currentComplexityBand = initialComplexityBand;
        this.levelPolicyVersion = DEFAULT_SCORING_POLICY_VERSION;
        this.generationPolicyVersion = DEFAULT_GENERATION_POLICY_VERSION;
        this.idempotencyKey = idempotencyKey;
        this.startedAt = startedAt;
        this.lastActivityAt = startedAt;
    }

    public static LevelTestSession start(
            User user,
            LevelTestSessionType sessionType,
            String originLanguage,
            String learningLanguage,
            int initialComplexityBand,
            String idempotencyKey,
            LocalDateTime startedAt
    ) {
        return new LevelTestSession(
                user,
                sessionType,
                originLanguage,
                learningLanguage,
                initialComplexityBand,
                idempotencyKey,
                startedAt
        );
    }

    public void markEvaluating(LocalDateTime now) {
        if (status == LevelTestSessionStatus.IN_PROGRESS) {
            status = LevelTestSessionStatus.EVALUATING;
        }
        lastActivityAt = now;
    }

    public void resumeAfterEvaluation(LocalDateTime now) {
        if (status == LevelTestSessionStatus.EVALUATING) {
            status = LevelTestSessionStatus.IN_PROGRESS;
        }
        lastActivityAt = now;
    }

    public void advance(int nextComplexityBand, LocalDateTime now) {
        if (status != LevelTestSessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Level Test가 진행 중 상태가 아닙니다.");
        }
        currentQuestionNumber = Math.min(
                totalQuestions + 1,
                currentQuestionNumber + 1
        );
        currentComplexityBand = Math.max(
                1,
                Math.min(5, nextComplexityBand)
        );
        lastActivityAt = now;
    }

    public void complete(
            int score,
            String proficiencyBand,
            LocalDateTime now
    ) {
        this.baseLevelScore = (double) score;
        this.proficiencyBand = proficiencyBand;
        this.status = LevelTestSessionStatus.COMPLETED;
        this.currentQuestionNumber = totalQuestions + 1;
        this.completedAt = now;
        this.lastActivityAt = now;
    }

    public void fail() {
        this.status = LevelTestSessionStatus.FAILED;
    }

    public int currentQuestionNumber() {
        return currentQuestionNumber == null ? 1 : currentQuestionNumber;
    }

    public int currentComplexityBand() {
        return currentComplexityBand == null ? 2 : currentComplexityBand;
    }
}
