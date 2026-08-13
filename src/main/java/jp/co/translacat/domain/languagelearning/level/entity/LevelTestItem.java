package jp.co.translacat.domain.languagelearning.level.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDifficulty;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_level_test_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_level_item_session_no",
                columnNames = {"session_id", "question_number"}
        ),
        indexes = @Index(
                name = "idx_ll_level_item_session",
                columnList = "session_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LevelTestItem extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private LevelTestSession session;

    @Column(name = "question_number", nullable = false)
    private int questionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LevelTestDifficulty difficulty;

    @Lob
    @Column(name = "origin_text", nullable = false, columnDefinition = "TEXT")
    private String originText;

    @Lob
    @Column(name = "focus_metrics_json", nullable = false, columnDefinition = "TEXT")
    private String focusMetricsJson;

    @Lob
    @Column(name = "focus_reason", nullable = false, columnDefinition = "TEXT")
    private String focusReason;

    @Column(name = "prompt_version", length = 100)
    private String promptVersion;

    @Lob
    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    private LocalDateTime answeredAt;

    private LevelTestItem(
            LevelTestSession session,
            int questionNumber,
            LevelTestDifficulty difficulty,
            String originText,
            String focusMetricsJson,
            String focusReason,
            String promptVersion
    ) {
        this.session = session;
        this.questionNumber = questionNumber;
        this.difficulty = difficulty;
        this.originText = originText;
        this.focusMetricsJson = focusMetricsJson;
        this.focusReason = focusReason;
        this.promptVersion = promptVersion;
    }

    public static LevelTestItem create(
            LevelTestSession session,
            int questionNumber,
            LevelTestDifficulty difficulty,
            String originText,
            String focusMetricsJson,
            String focusReason,
            String promptVersion
    ) {
        return new LevelTestItem(
                session,
                questionNumber,
                difficulty,
                originText,
                focusMetricsJson,
                focusReason,
                promptVersion
        );
    }

    public void answer(String answerText) {
        this.answerText = answerText;
        this.answeredAt = LocalDateTime.now();
    }

    public boolean isAnswered() {
        return answerText != null && !answerText.isBlank();
    }
}
