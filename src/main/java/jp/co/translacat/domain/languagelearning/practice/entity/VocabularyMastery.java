package jp.co.translacat.domain.languagelearning.practice.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@Table(
        name = "language_learning_vocabulary_mastery",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_vocabulary_mastery_user_key",
                columnNames = {"user_id", "canonical_key"}
        ),
        indexes = {
                @Index(name = "idx_ll_vocabulary_mastery_user", columnList = "user_id"),
                @Index(name = "idx_ll_vocabulary_mastery_user_score", columnList = "user_id,score")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VocabularyMastery extends BaseAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "canonical_key", nullable = false, length = 200)
    private String canonicalKey;

    @Column(name = "display_expression", nullable = false, length = 300)
    private String displayExpression;

    @Column(nullable = false)
    private double score;

    @Column(name = "evaluation_count", nullable = false)
    private int evaluationCount;

    @Column(name = "selected_count", nullable = false)
    private int selectedCount;

    @Column(name = "last_selected_date")
    private LocalDate lastSelectedDate;

    private VocabularyMastery(User user, String canonicalKey, String displayExpression) {
        this.user = user;
        this.canonicalKey = canonicalKey;
        this.displayExpression = displayExpression;
        this.score = 50.0;
    }

    public static VocabularyMastery create(User user, String canonicalKey, String displayExpression) {
        return new VocabularyMastery(user, canonicalKey, displayExpression);
    }

    public void markSelected(LocalDate date) {
        this.lastSelectedDate = date;
        this.selectedCount++;
    }

    public void applyScore(double newScore, double weight) {
        double next = score * (1 - weight) + newScore * weight;
        this.score = Math.round(next * 100.0) / 100.0;
        this.evaluationCount++;
    }
}
