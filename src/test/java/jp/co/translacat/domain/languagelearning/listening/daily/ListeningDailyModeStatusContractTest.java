package jp.co.translacat.domain.languagelearning.listening.daily;

import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ListeningDailyModeStatusContractTest {

    @Test
    void todayStatusSeparatesPreparationLearningAndEvaluationProgress() {
        var componentNames = Arrays.stream(
                        ListeningApiContract.DailyModeStatusView.class.getRecordComponents()
                )
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();

        assertThat(componentNames)
                .contains(
                        "submittedItemCount",
                        "terminalItemCount",
                        "evaluatedItemCount",
                        "answerRevealedItemCount",
                        "physicalItemCount",
                        "readyItemCount",
                        "targetItemCount"
                );
    }
}
