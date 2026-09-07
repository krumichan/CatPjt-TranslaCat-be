package jp.co.translacat.domain.languagelearning.practice.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeGeneratedQuestionDto;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeQuestionType;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "language_learning_practice_question",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_practice_question_set_order",
                columnNames = {"practice_set_id", "order_no"}
        ),
        indexes = @Index(name = "idx_ll_practice_question_set", columnList = "practice_set_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PracticeQuestion extends BaseAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "practice_set_id", nullable = false, updatable = false)
    private PracticeSet practiceSet;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 30)
    private PracticeQuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PracticeDifficulty difficulty;

    @Column(name = "complexity_band", nullable = false)
    private int complexityBand;

    @Column(name = "passage_id", length = 80)
    private String passageId;

    @Lob
    @Column(name = "passage_text", columnDefinition = "TEXT")
    private String passageText;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Lob
    @Column(name = "options_json", nullable = false, columnDefinition = "TEXT")
    private String optionsJson;

    @Lob
    @Column(name = "correct_answer_json", nullable = false, columnDefinition = "TEXT")
    private String correctAnswerJson;

    @Column(name = "skill_tag", nullable = false, length = 60)
    private String skillTag;

    @Lob
    @Column(name = "evidence_text", columnDefinition = "TEXT")
    private String evidenceText;

    @Lob
    @Column(name = "explanation_origin", nullable = false, columnDefinition = "TEXT")
    private String explanationOrigin;

    @Lob
    @Column(name = "explanation_learning", nullable = false, columnDefinition = "TEXT")
    private String explanationLearning;

    @Column(name = "target_expression", length = 300)
    private String targetExpression;

    @Column(name = "canonical_key", length = 200)
    private String canonicalKey;

    @Column(name = "review_target", nullable = false)
    private boolean reviewTarget;

    @Lob
    @Column(name = "vocabulary_candidates_json", nullable = false, columnDefinition = "TEXT")
    private String vocabularyCandidatesJson;

    public static PracticeQuestion create(
            PracticeSet set,
            PracticeGeneratedQuestionDto generated,
            String optionsJson,
            String correctAnswerJson,
            String vocabularyCandidatesJson
    ) {
        PracticeQuestion value = new PracticeQuestion();
        value.practiceSet = set;
        value.orderNo = generated.order();
        value.questionType = generated.questionType();
        value.difficulty = generated.difficulty();
        value.complexityBand = generated.complexityBand();
        value.passageId = generated.passageId();
        value.passageText = generated.passageText();
        value.prompt = generated.prompt();
        value.optionsJson = optionsJson;
        value.correctAnswerJson = correctAnswerJson;
        value.skillTag = generated.skillTag();
        value.evidenceText = generated.evidenceText();
        value.explanationOrigin = generated.explanationOrigin();
        value.explanationLearning = generated.explanationLearning();
        value.targetExpression = generated.targetExpression();
        value.canonicalKey = generated.canonicalKey();
        value.reviewTarget = generated.reviewTarget();
        value.vocabularyCandidatesJson = vocabularyCandidatesJson;
        return value;
    }
}
