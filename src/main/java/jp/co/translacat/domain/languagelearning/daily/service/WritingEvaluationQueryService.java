package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.common.enums.WritingEvaluationContext;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingEvaluationRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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
}
