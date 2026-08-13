package jp.co.translacat.domain.languagelearning.dashboard.service;

import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.MonthlyReportResponseDto;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.ScorePointResponseDto;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class DashboardScoreCalculator {

    public List<WritingEvaluation> filter(
            List<WritingEvaluation> evaluations,
            LocalDate from,
            LocalDate to
    ) {
        return evaluations.stream()
                .filter(evaluation -> {
                    LocalDate learningDate = learningDate(evaluation);
                    return learningDate != null
                            && !learningDate.isBefore(from)
                            && !learningDate.isAfter(to);
                })
                .toList();
    }

    public List<WritingEvaluation> filterByLearningDate(
            List<WritingEvaluation> evaluations,
            LocalDate learningDate
    ) {
        return evaluations.stream()
                .filter(evaluation -> learningDate.equals(
                        learningDate(evaluation)
                ))
                .toList();
    }

    public Double averageOverall(List<WritingEvaluation> evaluations) {
        if (evaluations.isEmpty()) {
            return null;
        }

        return round(evaluations.stream()
                .map(WritingEvaluation::getOverallScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0));
    }

    public List<ScorePointResponseDto> scoreTrend(
            List<WritingEvaluation> evaluations,
            LocalDate from,
            LocalDate to
    ) {
        Map<LocalDate, List<WritingEvaluation>> grouped = filter(
                evaluations,
                from,
                to
        ).stream().collect(Collectors.groupingBy(
                this::learningDate
        ));

        return grouped.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> toScorePoint(
                        entry.getKey(),
                        entry.getValue()
                ))
                .toList();
    }

    public MonthlyReportResponseDto monthlyReport(
            List<WritingEvaluation> evaluations,
            YearMonth month
    ) {
        List<WritingEvaluation> monthlyEvaluations = filter(
                evaluations,
                month.atDay(1),
                month.atEndOfMonth()
        );

        if (monthlyEvaluations.isEmpty()) {
            return new MonthlyReportResponseDto(
                    month,
                    0,
                    null,
                    null,
                    null
            );
        }

        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put(
                "MEANING",
                average(monthlyEvaluations, WritingEvaluation::getMeaningScore)
        );
        metrics.put(
                "GRAMMAR",
                average(monthlyEvaluations, WritingEvaluation::getGrammarScore)
        );
        metrics.put(
                "VOCABULARY",
                average(monthlyEvaluations, WritingEvaluation::getVocabularyScore)
        );
        metrics.put(
                "NATURALNESS",
                average(monthlyEvaluations, WritingEvaluation::getNaturalnessScore)
        );
        metrics.put(
                "EXPRESSION",
                average(monthlyEvaluations, WritingEvaluation::getExpressionScore)
        );

        String strongestMetric = metrics.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        String weakestMetric = metrics.entrySet()
                .stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        return new MonthlyReportResponseDto(
                month,
                monthlyEvaluations.size(),
                averageOverall(monthlyEvaluations),
                strongestMetric,
                weakestMetric
        );
    }

    private ScorePointResponseDto toScorePoint(
            LocalDate date,
            List<WritingEvaluation> evaluations
    ) {
        return new ScorePointResponseDto(
                date,
                average(evaluations, WritingEvaluation::getOverallScore),
                average(evaluations, WritingEvaluation::getMeaningScore),
                average(evaluations, WritingEvaluation::getGrammarScore),
                average(evaluations, WritingEvaluation::getVocabularyScore),
                average(evaluations, WritingEvaluation::getNaturalnessScore),
                average(evaluations, WritingEvaluation::getExpressionScore)
        );
    }

    private LocalDate learningDate(WritingEvaluation evaluation) {
        if (evaluation.getAnswer() == null) {
            return null;
        }

        return evaluation.getAnswer()
                .getDailyItem()
                .getDailySet()
                .getLearningDate();
    }

    private double average(
            List<WritingEvaluation> evaluations,
            ScoreGetter getter
    ) {
        return round(evaluations.stream()
                .map(getter::get)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @FunctionalInterface
    private interface ScoreGetter {
        Integer get(WritingEvaluation evaluation);
    }
}
