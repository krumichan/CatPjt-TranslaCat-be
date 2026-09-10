package jp.co.translacat.domain.languagelearning.listening.evaluation.validator;

import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningEvaluationContractPolicy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListeningEvaluationResponseValidatorTest {

    private final ListeningEvaluationResponseValidator validator =
            new ListeningEvaluationResponseValidator(
                    new ListeningEvaluationContractPolicy()
            );

    @ParameterizedTest
    @EnumSource(ListeningTaskType.class)
    void acceptsRequestedTaskAndAllOtherTasksNotSelected(ListeningTaskType selectedTask) {
        var tasks = Arrays.stream(ListeningTaskType.values())
                .map(type -> type == selectedTask ? notEvaluable(type) : notSelected(type))
                .toList();
        var response = response(tasks);

        assertThat(validator.validate(
                response,
                "request-1",
                "listening-profile",
                11L,
                22L,
                selectedTask
        ).taskType()).isEqualTo(selectedTask);
    }

    @Test
    void rejectsLegacyThreeTaskPayload() {
        var response = response(List.of(
                notEvaluable(ListeningTaskType.DICTATION),
                notSelected(ListeningTaskType.INTERPRETATION),
                notSelected(ListeningTaskType.REPEAT_AFTER_AUDIO)
        ));

        assertThatThrownBy(() -> validator.validate(
                response,
                "request-1",
                "listening-profile",
                11L,
                22L,
                ListeningTaskType.DICTATION
        )).hasMessageContaining("기본 계약");
    }

    @Test
    void rejectsSingleTaskPayload() {
        var response = response(List.of(
                notEvaluable(ListeningTaskType.DICTATION)
        ));

        assertThatThrownBy(() -> validator.validate(
                response,
                "request-1",
                "listening-profile",
                11L,
                22L,
                ListeningTaskType.DICTATION
        )).hasMessageContaining("기본 계약");
    }

    @Test
    void rejectsEvaluationPayloadOnNotSelectedTask() {
        var invalid = new AiListeningContract.TaskResult(
                ListeningTaskType.INTERPRETATION,
                "NOT_SELECTED",
                false,
                null,
                null,
                null,
                "INDEPENDENT",
                List.of(),
                List.of(),
                List.of(new AiListeningContract.MetricEvidence(
                        null, null, null, null,
                        "MEANING", "INFO", "unexpected"
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                Map.of()
        );
        var response = response(List.of(
                notEvaluable(ListeningTaskType.DICTATION),
                invalid,
                notSelected(ListeningTaskType.REPEAT_AFTER_AUDIO),
                notSelected(ListeningTaskType.COMPREHENSION),
                notSelected(ListeningTaskType.SUMMARY)
        ));

        assertThatThrownBy(() -> validator.validate(
                response,
                "request-1",
                "listening-profile",
                11L,
                22L,
                ListeningTaskType.DICTATION
        )).hasMessageContaining("선택하지 않은");
    }

    private AiListeningContract.EvaluationResponse response(
            List<AiListeningContract.TaskResult> tasks
    ) {
        return new AiListeningContract.EvaluationResponse(
                "request-1",
                11L,
                22L,
                "listening-evaluation",
                "listening-scoring-half-up",
                "listening-profile",
                tasks,
                new AiListeningContract.Overall(null, 0, 5),
                Map.of()
        );
    }

    private AiListeningContract.TaskResult notEvaluable(
            ListeningTaskType taskType
    ) {
        return new AiListeningContract.TaskResult(
                taskType,
                "NOT_EVALUABLE",
                false,
                null,
                0.69,
                "LOW_CONFIDENCE",
                "INDEPENDENT",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("다시 들어 보세요."),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                Map.of()
        );
    }

    private AiListeningContract.TaskResult notSelected(
            ListeningTaskType taskType
    ) {
        return new AiListeningContract.TaskResult(
                taskType,
                "NOT_SELECTED",
                false,
                null,
                null,
                null,
                "INDEPENDENT",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                Map.of()
        );
    }
}
