package jp.co.translacat.domain.languagelearning.level.policy;

import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;
import jp.co.translacat.global.exception.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LevelBaseScorePolicyTest {

    private final LevelBaseScorePolicy policy = new LevelBaseScorePolicy();

    @Test
    void calculatesAverageOverallScore() {
        double score = policy.calculate(
                List.of(
                        evaluation(70),
                        evaluation(80),
                        evaluation(90)
                ),
                3
        );

        assertThat(score).isEqualTo(80.0);
    }

    @Test
    void rejectsIncompleteLevelTestEvaluationSet() {
        assertThatThrownBy(() -> policy.calculate(
                List.of(evaluation(80)),
                2
        )).isInstanceOf(BusinessException.class);
    }

    private WritingEvaluation evaluation(int overallScore) {
        WritingEvaluation evaluation = WritingEvaluation.pendingDaily(
                null,
                null
        );
        evaluation.success(
                overallScore,
                overallScore,
                overallScore,
                overallScore,
                overallScore,
                overallScore,
                "[]",
                "[]",
                "[]",
                "[]",
                "{}",
                "{}",
                "rubric-v1",
                "scoring-v1",
                "prompt-v1"
        );

        return evaluation;
    }
}
