package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.common.enums.WritingEvaluationContext;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingEvaluationRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WritingEvaluationQueryServiceTest {

    @Mock
    private WritingEvaluationRepository evaluationRepository;
    @Mock
    private WritingEvaluation firstEvaluation;
    @Mock
    private WritingEvaluation secondEvaluation;
    @Mock
    private WritingEvaluation scoreMissingEvaluation;

    @Test
    void dailyAverageUsesSuccessfulOriginalLearningDateScores() {
        LocalDate learningDate = LocalDate.of(2026, 8, 27);
        when(evaluationRepository
                .findAllByAnswerDailyItemDailySetIdAndAnswerAttemptDateAndContextAndStatus(
                        100L,
                        learningDate,
                        WritingEvaluationContext.DAILY,
                        EvaluationStatus.SUCCESS
                ))
                .thenReturn(List.of(
                        firstEvaluation,
                        secondEvaluation,
                        scoreMissingEvaluation
                ));
        when(firstEvaluation.getOverallScore()).thenReturn(86);
        when(secondEvaluation.getOverallScore()).thenReturn(94);
        when(scoreMissingEvaluation.getOverallScore()).thenReturn(null);

        WritingEvaluationQueryService service =
                new WritingEvaluationQueryService(evaluationRepository);

        assertThat(service.findDailyAverageOverallScore(100L, learningDate))
                .isEqualTo(90.0);
    }

    @Test
    void dailyAverageIsNullWhenNoSuccessfulScoreExists() {
        LocalDate learningDate = LocalDate.of(2026, 8, 27);
        when(evaluationRepository
                .findAllByAnswerDailyItemDailySetIdAndAnswerAttemptDateAndContextAndStatus(
                        100L,
                        learningDate,
                        WritingEvaluationContext.DAILY,
                        EvaluationStatus.SUCCESS
                ))
                .thenReturn(List.of());

        WritingEvaluationQueryService service =
                new WritingEvaluationQueryService(evaluationRepository);

        assertThat(service.findDailyAverageOverallScore(100L, learningDate))
                .isNull();
    }

    @Test
    void dailyEvaluationStatusIsPendingWhenAnyEvaluationIsPending() {
        LocalDate learningDate = LocalDate.of(2026, 9, 6);
        when(evaluationRepository
                .findAllByAnswerDailyItemDailySetIdAndAnswerAttemptDateAndContext(
                        100L,
                        learningDate,
                        WritingEvaluationContext.DAILY
                ))
                .thenReturn(List.of(firstEvaluation, secondEvaluation));
        when(firstEvaluation.getStatus()).thenReturn(EvaluationStatus.SUCCESS);
        when(secondEvaluation.getStatus()).thenReturn(EvaluationStatus.PENDING);

        WritingEvaluationQueryService service =
                new WritingEvaluationQueryService(evaluationRepository);

        assertThat(service.resolveDailyEvaluationStatus(100L, learningDate))
                .isEqualTo("PENDING");
    }

    @Test
    void dailyEvaluationStatusIsFailedWhenNoPendingAndAnyEvaluationFailed() {
        LocalDate learningDate = LocalDate.of(2026, 9, 6);
        when(evaluationRepository
                .findAllByAnswerDailyItemDailySetIdAndAnswerAttemptDateAndContext(
                        100L,
                        learningDate,
                        WritingEvaluationContext.DAILY
                ))
                .thenReturn(List.of(firstEvaluation, secondEvaluation));
        when(firstEvaluation.getStatus()).thenReturn(EvaluationStatus.SUCCESS);
        when(secondEvaluation.getStatus()).thenReturn(EvaluationStatus.FAILED);

        WritingEvaluationQueryService service =
                new WritingEvaluationQueryService(evaluationRepository);

        assertThat(service.resolveDailyEvaluationStatus(100L, learningDate))
                .isEqualTo("FAILED");
    }

}
