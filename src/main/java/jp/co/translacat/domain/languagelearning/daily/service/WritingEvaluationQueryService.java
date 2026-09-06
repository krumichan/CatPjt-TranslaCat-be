package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.common.enums.WritingEvaluationContext;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingEvaluationRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WritingEvaluationQueryService {

    private final WritingEvaluationRepository evaluationRepository;

    public Double findDailyAverageOverallScore(
            Long dailySetId,
            LocalDate learningDate
    ) {
        var average = evaluationRepository
                .findAllByAnswerDailyItemDailySetIdAndAnswerAttemptDateAndContextAndStatus(
                        dailySetId,
                        learningDate,
                        WritingEvaluationContext.DAILY,
                        EvaluationStatus.SUCCESS
                )
                .stream()
                .map(WritingEvaluation::getOverallScore)
                .filter(score -> score != null)
                .mapToInt(Integer::intValue)
                .average();

        return average.isPresent() ? average.getAsDouble() : null;
    }

    public String resolveDailyEvaluationStatus(
            Long dailySetId,
            LocalDate learningDate
    ) {
        List<WritingEvaluation> evaluations = evaluationRepository
                .findAllByAnswerDailyItemDailySetIdAndAnswerAttemptDateAndContext(
                        dailySetId,
                        learningDate,
                        WritingEvaluationContext.DAILY
                );
        if (evaluations.isEmpty()) {
            return "NOT_STARTED";
        }
        if (evaluations.stream().anyMatch(value ->
                value.getStatus() == EvaluationStatus.PENDING)) {
            return EvaluationStatus.PENDING.name();
        }
        if (evaluations.stream().anyMatch(value ->
                value.getStatus() == EvaluationStatus.FAILED)) {
            return EvaluationStatus.FAILED.name();
        }
        if (evaluations.stream().allMatch(value ->
                value.getStatus() == EvaluationStatus.SUCCESS)) {
            return EvaluationStatus.SUCCESS.name();
        }
        return "IN_PROGRESS";
    }
}
