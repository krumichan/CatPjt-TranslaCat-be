package jp.co.translacat.domain.languagelearning.daily.entity;

import jakarta.persistence.*;

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
        name = "language_learning_writing_answer",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_answer_item_attempt_date",
                columnNames = {"daily_item_id", "attempt_date"}
        ),
        indexes = {
                @Index(
                        name = "idx_ll_answer_user",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_ll_answer_item",
                        columnList = "daily_item_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WritingAnswer extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_item_id", nullable = false, updatable = false)
    private DailyWritingItem dailyItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "attempt_date", nullable = false)
    private LocalDate attemptDate;

    @Lob
    @Column(name = "answer_text", nullable = false, columnDefinition = "TEXT")
    private String answerText;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    private WritingAnswer(
            User user,
            DailyWritingItem dailyItem,
            LocalDate attemptDate,
            String answerText
    ) {
        this.user = user;
        this.dailyItem = dailyItem;
        this.attemptDate = attemptDate;
        this.answerText = answerText;
        this.submittedAt = LocalDateTime.now();
    }

    public static WritingAnswer create(
            User user,
            DailyWritingItem dailyItem,
            LocalDate attemptDate,
            String answerText
    ) {
        return new WritingAnswer(
                user,
                dailyItem,
                attemptDate,
                answerText
        );
    }

    public void updateAnswer(String answerText) {
        this.answerText = answerText;
        this.submittedAt = LocalDateTime.now();
    }
}
