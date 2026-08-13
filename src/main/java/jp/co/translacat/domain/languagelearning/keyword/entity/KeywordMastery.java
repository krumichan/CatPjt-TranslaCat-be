package jp.co.translacat.domain.languagelearning.keyword.entity;

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
        name = "language_learning_keyword_mastery",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_keyword_mastery_user_key",
                columnNames = {"user_id", "canonical_key"}
        ),
        indexes = @Index(
                name = "idx_ll_keyword_mastery_user",
                columnList = "user_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KeywordMastery extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "canonical_key", nullable = false, length = 200)
    private String canonicalKey;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private int evaluationCount;

    private LocalDate lastSelectedDate;

    @Column(nullable = false)
    private int selectedCount;

    private KeywordMastery(User user, String canonicalKey) {
        this.user = user;
        this.canonicalKey = canonicalKey;
        this.score = 50.0;
    }

    public static KeywordMastery create(
            User user,
            String canonicalKey
    ) {
        return new KeywordMastery(user, canonicalKey);
    }

    public void markSelected(LocalDate date) {
        this.lastSelectedDate = date;
        this.selectedCount++;
    }

    public void applyScore(
            double newScore,
            double weight
    ) {
        double nextScore = evaluationCount == 0
                ? newScore
                : score * (1 - weight) + newScore * weight;

        this.score = round(nextScore);
        this.evaluationCount++;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
