package jp.co.translacat.domain.languagelearning.practice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeAttempt;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeQuestion;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeSet;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeAttemptRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeMetricScoreRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeQuestionRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeSetRepository;
import jp.co.translacat.domain.languagelearning.setting.port.UserSettingsGateway;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReadingPassageExpressionQueryTest {
    @Test
    void queryHidesCandidatesUntilEntirePassageAnsweredAndNeverChangesHistoricalScore() {
        var questions = mock(PracticeQuestionRepository.class);
        var attempts = mock(PracticeAttemptRepository.class);
        var service = new PracticeQueryService(mock(PracticeSetRepository.class), questions, attempts,
                mock(PracticeMetricScoreRepository.class), new LanguageLearningJsonCodec(new ObjectMapper()),
                mock(UserSettingsGateway.class));
        var set = mock(PracticeSet.class);
        when(set.getId()).thenReturn(12L);
        when(set.getDomain()).thenReturn(PracticeDomain.READING);
        when(set.getQuestionCount()).thenReturn(5);
        when(set.getOfficialScore()).thenReturn(null);
        var stored = IntStream.rangeClosed(1, 3).mapToObj(order -> question(order)).toList();
        when(questions.findAllByPracticeSetIdOrderByOrderNoAsc(12L)).thenReturn(stored);
        var answer = mock(PracticeAttempt.class);
        when(answer.getAnswerJson()).thenReturn("[\"A\"]");
        when(attempts.findAllByQuestionIdOrderByAttemptNoAsc(1L)).thenReturn(List.of(answer));
        when(attempts.findAllByQuestionIdOrderByAttemptNoAsc(2L)).thenReturn(List.of(answer));
        assertThat(service.toResponse(set).questions()).allSatisfy(
                item -> assertThat(item.vocabularyCandidates()).isEmpty());

        when(attempts.findAllByQuestionIdOrderByAttemptNoAsc(3L)).thenReturn(List.of(answer));
        when(stored.get(1).getVocabularyCandidatesJson()).thenReturn("{malformed optional field");
        var response = service.toResponse(set);
        assertThat(response.questions().getFirst().vocabularyCandidates()).containsExactly("計画");
        assertThat(response.questions().get(1).vocabularyCandidates()).isEmpty();
        assertThat(response.officialScore()).isNull();
        assertThat(response.questionCount()).isEqualTo(5);
        verify(questions, never()).save(any());
    }

    private PracticeQuestion question(int order) {
        var question = mock(PracticeQuestion.class);
        when(question.getId()).thenReturn((long) order);
        when(question.getOrderNo()).thenReturn(order);
        when(question.getPassageId()).thenReturn("p1");
        when(question.getPassageText()).thenReturn("計画を確認する。計画は変わらない。");
        when(question.getOptionsJson()).thenReturn("[]");
        when(question.getCorrectAnswerJson()).thenReturn("[\"A\"]");
        when(question.getVocabularyCandidatesJson()).thenReturn("[\"計画\",\"計画\",\"missing\"]");
        return question;
    }
}
