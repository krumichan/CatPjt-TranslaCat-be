package jp.co.translacat.domain.languagelearning.level.policy;

import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LevelBaseScorePolicy {
    public static final String VERSION = "LEVEL_BASE_V1";

    /**
     * V1: Adaptive Level Test 12문항의 Overall Score 단순 평균.
     * 정책을 별도 Component/Version으로 분리하여 추후 CEFR band 또는
     * 난이도 보정식을 도입해도 기존 Session 이력을 보존한다.
     */
    public double calculate(List<WritingEvaluation> evaluations, int expectedCount) {
        if (evaluations.size() < expectedCount) {
            throw new BusinessException(
                    "Level Test 평가 결과가 부족합니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }
        return Math.round(
                evaluations.stream()
                        .map(WritingEvaluation::getOverallScore)
                        .mapToInt(Integer::intValue)
                        .average()
                        .orElse(0.0) * 100.0
        ) / 100.0;
    }
}
