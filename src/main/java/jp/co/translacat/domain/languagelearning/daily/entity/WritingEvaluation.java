package jp.co.translacat.domain.languagelearning.daily.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.common.enums.WritingEvaluationContext;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_writing_evaluation",
        indexes = {
                @Index(
                        name = "idx_ll_eval_user_context",
                        columnList = "user_id,evaluation_context"
                ),
                @Index(
                        name = "idx_ll_eval_answer",
                        columnList = "answer_id"
                ),
                @Index(
                        name = "idx_ll_eval_level_item",
                        columnList = "level_test_item_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WritingEvaluation extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_context", nullable = false, length = 30)
    private WritingEvaluationContext context;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", unique = true)
    private WritingAnswer answer;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_test_item_id", unique = true)
    private LevelTestItem levelTestItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvaluationStatus status;

    private Integer overallScore;
    private Integer meaningScore;
    private Integer grammarScore;
    private Integer vocabularyScore;
    private Integer naturalnessScore;
    private Integer expressionScore;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String strengthsJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String weaknessesJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String correctionsJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String recommendedAnswersJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String explanationJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String profileSignalsJson;

    @Column(length = 100)
    private String evaluationRubricVersion;

    @Column(length = 100)
    private String scoringPolicyVersion;

    @Column(length = 100)
    private String promptVersion;

    private LocalDateTime evaluatedAt;

    @Column(length = 1000)
    private String failureMessage;

    private WritingEvaluation(
            User user,
            WritingEvaluationContext context,
            WritingAnswer answer,
            LevelTestItem levelTestItem
    ) {
        this.user = user;
        this.context = context;
        this.answer = answer;
        this.levelTestItem = levelTestItem;
        this.status = EvaluationStatus.PENDING;
    }

    public static WritingEvaluation pendingDaily(
            User user,
            WritingAnswer answer
    ) {
        return new WritingEvaluation(
                user,
                WritingEvaluationContext.DAILY,
                answer,
                null
        );
    }

    public static WritingEvaluation pendingLevel(
            User user,
            LevelTestItem levelTestItem
    ) {
        return new WritingEvaluation(
                user,
                WritingEvaluationContext.LEVEL_TEST,
                null,
                levelTestItem
        );
    }

    public void success(
            int overallScore,
            int meaningScore,
            int grammarScore,
            int vocabularyScore,
            int naturalnessScore,
            int expressionScore,
            String strengthsJson,
            String weaknessesJson,
            String correctionsJson,
            String recommendedAnswersJson,
            String explanationJson,
            String profileSignalsJson,
            String evaluationRubricVersion,
            String scoringPolicyVersion,
            String promptVersion
    ) {
        this.overallScore = overallScore;
        this.meaningScore = meaningScore;
        this.grammarScore = grammarScore;
        this.vocabularyScore = vocabularyScore;
        this.naturalnessScore = naturalnessScore;
        this.expressionScore = expressionScore;
        this.strengthsJson = strengthsJson;
        this.weaknessesJson = weaknessesJson;
        this.correctionsJson = correctionsJson;
        this.recommendedAnswersJson = recommendedAnswersJson;
        this.explanationJson = explanationJson;
        this.profileSignalsJson = profileSignalsJson;
        this.evaluationRubricVersion = evaluationRubricVersion;
        this.scoringPolicyVersion = scoringPolicyVersion;
        this.promptVersion = promptVersion;
        this.status = EvaluationStatus.SUCCESS;
        this.evaluatedAt = LocalDateTime.now();
        this.failureMessage = null;
    }

    public void fail(String message) {
        this.status = EvaluationStatus.FAILED;
        this.failureMessage = message;
        this.evaluatedAt = LocalDateTime.now();
    }
}
