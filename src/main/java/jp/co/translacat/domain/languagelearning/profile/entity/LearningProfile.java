package jp.co.translacat.domain.languagelearning.profile.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.LearningProfileState;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@Table(
        name = "language_learning_profile",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_profile_user",
                columnNames = "user_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningProfile extends BaseAuditable {

    private static final int CALIBRATION_DAYS = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, length = 30)
    private String profileVersion = "PROFILE_V1";

    private Double baseLevelScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LearningProfileState state = LearningProfileState.LEVEL_TEST_REQUIRED;

    private LocalDate calibrationStartedDate;
    private LocalDate calibrationCompletedDate;

    private Double meaningScore;
    private Double grammarScore;
    private Double vocabularyScore;
    private Double naturalnessScore;
    private Double expressionScore;

    private Double reviewPerformance;
    private Double normalPerformance;
    private Double challengePerformance;

    @Column(nullable = false)
    private int evaluationCount;

    @Column(nullable = false)
    private double confidence;

    @Column(length = 30)
    private String trend = "stable";

    @Lob
    @Column(columnDefinition = "TEXT")
    private String additionalSignalsJson = "{}";

    private LearningProfile(User user) {
        this.user = user;
    }

    public static LearningProfile create(User user) {
        return new LearningProfile(user);
    }

    public void completeLevelTest(double baseLevelScore) {
        this.baseLevelScore = round(baseLevelScore);
        this.state = LearningProfileState.CALIBRATING;
        this.calibrationStartedDate = null;
        this.calibrationCompletedDate = null;
    }

    public void startCalibrationIfNeeded(LocalDate learningDate) {
        if (state != LearningProfileState.CALIBRATING) {
            return;
        }
        if (calibrationStartedDate != null) {
            return;
        }

        this.calibrationStartedDate = learningDate;
    }

    public void advanceCalibration(LocalDate learningDate) {
        if (state != LearningProfileState.CALIBRATING) {
            return;
        }
        if (calibrationStartedDate == null) {
            return;
        }
        if (learningDate.isBefore(calibrationStartedDate.plusDays(CALIBRATION_DAYS))) {
            return;
        }

        this.state = LearningProfileState.ACTIVE;
        this.calibrationCompletedDate = learningDate;
    }

    public void resetForRecheck() {
        this.state = LearningProfileState.LEVEL_TEST_REQUIRED;
        this.calibrationStartedDate = null;
        this.calibrationCompletedDate = null;
    }

    public void applyScores(
            double meaning,
            double grammar,
            double vocabulary,
            double naturalness,
            double expression,
            double weight,
            DailyWritingDifficulty difficulty
    ) {
        double previousAverage = averageSkill();

        this.meaningScore = blend(meaningScore, meaning, weight);
        this.grammarScore = blend(grammarScore, grammar, weight);
        this.vocabularyScore = blend(vocabularyScore, vocabulary, weight);
        this.naturalnessScore = blend(naturalnessScore, naturalness, weight);
        this.expressionScore = blend(expressionScore, expression, weight);

        applyDifficultyPerformance(
                difficulty,
                average(
                        meaning,
                        grammar,
                        vocabulary,
                        naturalness,
                        expression
                ),
                weight
        );

        this.evaluationCount++;
        this.confidence = Math.min(1.0, evaluationCount / 20.0);
        this.trend = resolveTrend(
                previousAverage,
                averageSkill()
        );
    }

    public void setAdditionalSignalsJson(String json) {
        this.additionalSignalsJson = json == null ? "{}" : json;
    }

    private void applyDifficultyPerformance(
            DailyWritingDifficulty difficulty,
            double score,
            double weight
    ) {
        if (difficulty == null) {
            return;
        }

        switch (difficulty) {
            case REVIEW -> reviewPerformance = blend(
                    reviewPerformance,
                    score,
                    weight
            );
            case NORMAL -> normalPerformance = blend(
                    normalPerformance,
                    score,
                    weight
            );
            case CHALLENGE -> challengePerformance = blend(
                    challengePerformance,
                    score,
                    weight
            );
        }
    }

    private double averageSkill() {
        Double[] scores = {
                meaningScore,
                grammarScore,
                vocabularyScore,
                naturalnessScore,
                expressionScore
        };
        double sum = 0;
        int count = 0;

        for (Double score : scores) {
            if (score == null) {
                continue;
            }

            sum += score;
            count++;
        }

        return count == 0 ? 0 : sum / count;
    }

    private String resolveTrend(
            double previousAverage,
            double currentAverage
    ) {
        if (currentAverage - previousAverage > 3) {
            return "improving";
        }
        if (previousAverage - currentAverage > 3) {
            return "declining";
        }

        return "stable";
    }

    private static double average(double... values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }

        return sum / values.length;
    }

    private static Double blend(
            Double previous,
            double current,
            double weight
    ) {
        double value = previous == null
                ? current
                : previous * (1 - weight) + current * weight;

        return round(value);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
