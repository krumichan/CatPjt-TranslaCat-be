package jp.co.translacat.domain.languagelearning.practice.entity;

import jakarta.persistence.*;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "language_learning_practice_metric_score",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_practice_metric_set_tag",
                columnNames = {"practice_set_id", "skill_tag"}
        ),
        indexes = @Index(name = "idx_ll_practice_metric_tag", columnList = "skill_tag")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PracticeMetricScore extends BaseAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "practice_set_id", nullable = false, updatable = false)
    private PracticeSet practiceSet;

    @Column(name = "skill_tag", nullable = false, length = 60)
    private String skillTag;

    @Column(nullable = false)
    private double score;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    public static PracticeMetricScore create(
            PracticeSet practiceSet,
            String skillTag,
            double score,
            int sampleCount
    ) {
        PracticeMetricScore value = new PracticeMetricScore();
        value.practiceSet = practiceSet;
        value.skillTag = skillTag;
        value.score = score;
        value.sampleCount = sampleCount;
        return value;
    }
}
