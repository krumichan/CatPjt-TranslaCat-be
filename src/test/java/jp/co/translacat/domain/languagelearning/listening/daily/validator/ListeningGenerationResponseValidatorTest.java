package jp.co.translacat.domain.languagelearning.listening.daily.validator;

import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningLearningMode;
import jp.co.translacat.global.exception.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListeningGenerationResponseValidatorTest {

    private final ListeningGenerationResponseValidator validator =
            new ListeningGenerationResponseValidator();

    @Test
    void acceptsSafeUniqueCompleteItems() {
        validator.validate(
                response(List.of(item(1, "hash-1"), item(2, "hash-2"))),
                "request-1",
                "policy-v1",
                "model-v1",
                2,
                ListeningLearningMode.DICTATION,
                1,
                30
        );
    }

    @Test
    void rejectsDuplicateHashesAndUnsafeItems() {
        assertThatThrownBy(() -> validator.validate(
                response(List.of(item(1, "same"), item(2, "same"))),
                "request-1",
                "policy-v1",
                "model-v1",
                2,
                ListeningLearningMode.DICTATION,
                1,
                30
        )).isInstanceOf(BusinessException.class);

        var unsafe = new AiListeningContract.GeneratedItem(
                1, "hello", "hello", List.of("안녕", "안녕하세요"), List.of("hello"),
                List.of(), 2.0, "hash", "similarity",
                new AiListeningContract.Safety(false, List.of("unsafe"))
        );
        assertThatThrownBy(() -> validator.validate(
                response(List.of(unsafe)),
                "request-1",
                "policy-v1",
                "model-v1",
                1,
                ListeningLearningMode.DICTATION,
                1,
                30
        )).isInstanceOf(BusinessException.class);
    }


    @Test
    void validatesModeSpecificPayloads() {
        var options = List.of(
                new AiListeningContract.ChoiceOption("A", "one"),
                new AiListeningContract.ChoiceOption("B", "two"),
                new AiListeningContract.ChoiceOption("C", "three"),
                new AiListeningContract.ChoiceOption("D", "four")
        );
        var comprehension = fullItem(
                1, "hash-c", "What did the speaker decide?", options,
                "B", "DETAIL", List.of()
        );
        validator.validate(
                response(List.of(comprehension)),
                "request-1", "policy-v1", "model-v1", 1,
                ListeningLearningMode.COMPREHENSION, 1, 30
        );

        var summary = fullItem(
                1, "hash-s", null, List.of(), null, null,
                List.of("meeting moved", "confirm attendance")
        );
        validator.validate(
                response(List.of(summary)),
                "request-1", "policy-v1", "model-v1", 1,
                ListeningLearningMode.SUMMARY, 1, 30
        );

        assertThatThrownBy(() -> validator.validate(
                response(List.of(comprehension)),
                "request-1", "policy-v1", "model-v1", 1,
                ListeningLearningMode.DICTATION, 1, 30
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsComprehensionWithoutExactAbcdOptions() {
        var invalid = fullItem(
                1, "hash-invalid", "Choose one",
                List.of(
                        new AiListeningContract.ChoiceOption("A", "one"),
                        new AiListeningContract.ChoiceOption("B", "two"),
                        new AiListeningContract.ChoiceOption("C", "three"),
                        new AiListeningContract.ChoiceOption("E", "four")
                ),
                "B", "GIST", List.of()
        );
        assertThatThrownBy(() -> validator.validate(
                response(List.of(invalid)),
                "request-1", "policy-v1", "model-v1", 1,
                ListeningLearningMode.COMPREHENSION, 1, 30
        )).isInstanceOf(BusinessException.class);
    }

    private AiListeningContract.GenerationResponse response(
            List<AiListeningContract.GeneratedItem> items
    ) {
        return new AiListeningContract.GenerationResponse(
                "request-1",
                "generation-v1",
                "policy-v1",
                "model-v1",
                items,
                Map.of()
        );
    }


    private AiListeningContract.GeneratedItem fullItem(
            int index,
            String hash,
            String question,
            List<AiListeningContract.ChoiceOption> options,
            String correctOptionKey,
            String focus,
            List<String> summaryKeyPoints
    ) {
        return new AiListeningContract.GeneratedItem(
                index,
                "hello " + index,
                "hello " + index,
                List.of("안녕", "안녕하세요"),
                List.of("hello"),
                List.of(),
                2.0,
                hash,
                "similarity-" + index,
                new AiListeningContract.Safety(true, List.of()),
                null,
                null,
                question,
                options,
                correctOptionKey,
                focus,
                summaryKeyPoints
        );
    }

    private AiListeningContract.GeneratedItem item(int index, String hash) {
        return new AiListeningContract.GeneratedItem(
                index,
                "hello " + index,
                "hello " + index,
                List.of("안녕", "안녕하세요"),
                List.of("hello"),
                List.of(),
                2.0,
                hash,
                "similarity-" + index,
                new AiListeningContract.Safety(true, List.of())
        );
    }
}
