package jp.co.translacat.domain.languagelearning.profile.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.RecentEvaluationSummaryDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.WritingSkillScoresDto;
import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.common.enums.ProfileSignalType;
import jp.co.translacat.domain.languagelearning.common.enums.WritingEvaluationContext;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingEvaluationRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecentWritingEvaluationQueryService {

    private static final int RECENT_EVALUATION_LIMIT = 20;
    private static final int RECOMMENDED_FOCUS_LIMIT = 10;

    private final WritingEvaluationRepository evaluationRepository;
    private final LearningProfileSignalService signalService;

    public RecentEvaluationSummaryDto getSummary(Long userId) {
        List<WritingEvaluation> evaluations = evaluationRepository
                .findTop20ByUserIdAndContextAndStatusOrderByEvaluatedAtDesc(
                        userId,
                        WritingEvaluationContext.DAILY,
                        EvaluationStatus.SUCCESS
                );

        if (evaluations.isEmpty()) {
            return emptySummary();
        }

        return new RecentEvaluationSummaryDto(
                Math.min(evaluations.size(), RECENT_EVALUATION_LIMIT),
                round(average(
                        evaluations,
                        WritingEvaluation::getOverallScore
                )),
                toSkillScores(evaluations),
                signalService.getKeys(
                        userId,
                        ProfileSignalType.RECOMMENDED_FOCUS,
                        RECOMMENDED_FOCUS_LIMIT
                )
        );
    }

    private RecentEvaluationSummaryDto emptySummary() {
        return new RecentEvaluationSummaryDto(
                0,
                null,
                null,
                List.of()
        );
    }

    private WritingSkillScoresDto toSkillScores(
            List<WritingEvaluation> evaluations
    ) {
        return new WritingSkillScoresDto(
                round(average(evaluations, WritingEvaluation::getMeaningScore)),
                round(average(evaluations, WritingEvaluation::getGrammarScore)),
                round(average(evaluations, WritingEvaluation::getVocabularyScore)),
                round(average(evaluations, WritingEvaluation::getNaturalnessScore)),
                round(average(evaluations, WritingEvaluation::getExpressionScore))
        );
    }

    private double average(
            List<WritingEvaluation> evaluations,
            ScoreGetter getter
    ) {
        return evaluations.stream()
                .map(getter::get)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @FunctionalInterface
    private interface ScoreGetter {
        Integer get(WritingEvaluation evaluation);
    }
}
