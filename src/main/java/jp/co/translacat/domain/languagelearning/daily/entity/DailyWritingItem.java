package jp.co.translacat.domain.languagelearning.daily.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingDifficulty;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "language_learning_daily_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_daily_item_set_order",
                columnNames = {"daily_set_id", "item_order"}
        ),
        indexes = @Index(
                name = "idx_ll_daily_item_set",
                columnList = "daily_set_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyWritingItem extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_set_id", nullable = false, updatable = false)
    private DailyWritingSet dailySet;

    @Column(name = "item_order", nullable = false)
    private int orderNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DailyWritingDifficulty difficulty;

    @Lob
    @Column(name = "origin_text", nullable = false, columnDefinition = "TEXT")
    private String originText;

    @Lob
    @Column(name = "keywords_json", nullable = false, columnDefinition = "TEXT")
    private String keywordsJson;

    @Lob
    @Column(name = "focus_metrics_json", nullable = false, columnDefinition = "TEXT")
    private String focusMetricsJson;

    @Lob
    @Column(name = "focus_reason", nullable = false, columnDefinition = "TEXT")
    private String focusReason;

    @Lob
    @Column(name = "provided_facts_json", columnDefinition = "TEXT")
    private String providedFactsJson;

    @Lob
    @Column(name = "required_intents_json", columnDefinition = "TEXT")
    private String requiredIntentsJson;

    @Lob
    @Column(name = "response_constraints_json", columnDefinition = "TEXT")
    private String responseConstraintsJson;

    private DailyWritingItem(
            DailyWritingSet dailySet,
            int orderNo,
            DailyWritingDifficulty difficulty,
            String originText,
            String keywordsJson,
            String focusMetricsJson,
            String focusReason,
            String providedFactsJson,
            String requiredIntentsJson,
            String responseConstraintsJson
    ) {
        this.dailySet = dailySet;
        this.orderNo = orderNo;
        this.difficulty = difficulty;
        this.originText = originText;
        this.keywordsJson = keywordsJson;
        this.focusMetricsJson = focusMetricsJson;
        this.focusReason = focusReason;
        this.providedFactsJson = providedFactsJson;
        this.requiredIntentsJson = requiredIntentsJson;
        this.responseConstraintsJson = responseConstraintsJson;
    }

    public static DailyWritingItem create(
            DailyWritingSet dailySet,
            int orderNo,
            DailyWritingDifficulty difficulty,
            String originText,
            String keywordsJson,
            String focusMetricsJson,
            String focusReason,
            String providedFactsJson,
            String requiredIntentsJson,
            String responseConstraintsJson
    ) {
        return new DailyWritingItem(
                dailySet,
                orderNo,
                difficulty,
                originText,
                keywordsJson,
                focusMetricsJson,
                focusReason,
                providedFactsJson,
                requiredIntentsJson,
                responseConstraintsJson
        );
    }

    public void replace(
            DailyWritingDifficulty difficulty,
            String originText,
            String keywordsJson,
            String focusMetricsJson,
            String focusReason,
            String providedFactsJson,
            String requiredIntentsJson,
            String responseConstraintsJson
    ) {
        this.difficulty = difficulty;
        this.originText = originText;
        this.keywordsJson = keywordsJson;
        this.focusMetricsJson = focusMetricsJson;
        this.focusReason = focusReason;
        this.providedFactsJson = providedFactsJson;
        this.requiredIntentsJson = requiredIntentsJson;
        this.responseConstraintsJson = responseConstraintsJson;
    }
}
