package jp.co.translacat.domain.languagelearning.practice.entity;

import jakarta.persistence.*;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_practice_attempt",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_practice_attempt_question_no",
                columnNames = {"question_id", "attempt_no"}
        ),
        indexes = @Index(name = "idx_ll_practice_attempt_question", columnList = "question_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PracticeAttempt extends BaseAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false, updatable = false)
    private PracticeQuestion question;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Lob
    @Column(name = "answer_json", nullable = false, columnDefinition = "TEXT")
    private String answerJson;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    public static PracticeAttempt create(
            PracticeQuestion question,
            int attemptNo,
            String answerJson,
            boolean correct
    ) {
        PracticeAttempt value = new PracticeAttempt();
        value.question = question;
        value.attemptNo = attemptNo;
        value.answerJson = answerJson;
        value.correct = correct;
        value.submittedAt = LocalDateTime.now();
        return value;
    }

    public boolean isOfficial() {
        return attemptNo == 1;
    }
}
