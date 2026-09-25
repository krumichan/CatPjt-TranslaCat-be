package jp.co.translacat.domain.languagelearning.practice.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeQuestion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadingPassageExpressionPolicyTest {
    private final LanguageLearningJsonCodec codec = new LanguageLearningJsonCodec(new ObjectMapper());

    @Test
    void malformedOrAbsentOptionalMetadataNeverPreventsReading() {
        for (String json : List.of("{broken", "null", "{}", "[null,1,true]")) {
            assertThat(ReadingPassageExpressionPolicy.readCandidates(codec, json, "元の本文")).isEmpty();
        }
        assertThat(ReadingPassageExpressionPolicy.readCandidates(codec, null, "元の本文")).isEmpty();
    }

    @Test
    void onlyUniqueExactSurfaceFormsRemainAndRepeatedOccurrenceIsNotAnOffsetClaim() {
        assertThat(ReadingPassageExpressionPolicy.readCandidates(codec,
                "[\" 計画 \",\"計画\",\"計画した\",\"変更\",null]",
                "計画を変更する。計画を確認する。")).containsExactly("計画", "変更");
    }

    @Test
    void optionalCandidatesDoNotReleaseUntilAllPlannedPassageQuestionsAnswered() {
        var questions = IntStream.rangeClosed(1, 5).mapToObj(this::question).toList();
        assertThat(ReadingPassageExpressionPolicy.completedPassages(questions,
                q -> Set.of(1, 2).contains(q.getOrderNo()))).isEmpty();
        assertThat(ReadingPassageExpressionPolicy.completedPassages(questions,
                q -> Set.of(1, 2, 3).contains(q.getOrderNo()))).containsExactly("p1");
        assertThat(ReadingPassageExpressionPolicy.completedPassages(questions.subList(0, 2), q -> true)).isEmpty();
        assertThat(ReadingPassageExpressionPolicy.completedPassages(questions, q -> true)).containsExactlyInAnyOrder(
                "p1", "p2");
    }

    private PracticeQuestion question(int order) {
        PracticeQuestion question = mock(PracticeQuestion.class);
        when(question.getOrderNo()).thenReturn(order);
        when(question.getPassageId()).thenReturn(order <= 3 ? "p1" : "p2");
        when(question.getPassageText()).thenReturn("計画を変更する。計画を確認する。");
        return question;
    }
}
