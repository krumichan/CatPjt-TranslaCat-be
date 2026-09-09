package jp.co.translacat.domain.languagelearning.practice.service;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeSetStatus;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeQuestion;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeSet;
import jp.co.translacat.domain.languagelearning.practice.dto.request.PracticeAnswerSubmitRequestDto;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeAttemptRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeQuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PracticeCompletionGuardTest {
    @Mock private PracticeQuestionRepository questionRepository;
    @Mock private PracticeAttemptRepository attemptRepository;
    private PracticeAnswerCommandService service;
    private PracticeSet set;

    @BeforeEach
    void setup() {
        service = new PracticeAnswerCommandService(questionRepository, null, attemptRepository,
                null, null, null, null, null, null, null);
        set = PracticeSet.create(null, LocalDate.of(2026, 9, 9), PracticeDomain.READING,
                "COMPREHENSION", "ko", "ja", 5, 3);
        ReflectionTestUtils.setField(set, "id", 12L);
    }

    @Test
    void oneAvailableAnsweredQuestionCannotCompleteFiveQuestionSet() {
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L)).thenReturn(List.of(question(1)));

        Boolean completed = ReflectionTestUtils.invokeMethod(service, "finalizeSetIfReady", set);

        assertThat(completed).isFalse();
        assertThat(set.getStatus()).isEqualTo(PracticeSetStatus.ACTIVE);
        verifyNoInteractions(attemptRepository);
    }

    @Test
    void countAloneDoesNotHideMissingGlobalQuestionIndex() {
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of(question(1), question(2), question(3), question(4), question(6)));

        Boolean completed = ReflectionTestUtils.invokeMethod(service, "finalizeSetIfReady", set);

        assertThat(completed).isFalse();
        verifyNoInteractions(attemptRepository);
    }

    @Test
    void allGeneratedQuestionsStillNeedAllOfficialAnswers() {
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of(question(1), question(2), question(3), question(4), question(5)));
        when(attemptRepository.countByQuestionPracticeSetIdAndAttemptNo(12L, 1)).thenReturn(4L);

        Boolean completed = ReflectionTestUtils.invokeMethod(service, "finalizeSetIfReady", set);

        assertThat(completed).isFalse();
        assertThat(set.getCompletedAt()).isNull();
    }

    @Test
    void postLockReadsDoNotReusePreLockRepeatableReadSnapshot() throws Exception {
        var method = PracticeAnswerCommandService.class.getMethod(
                "submit", Long.class, Long.class, PracticeAnswerSubmitRequestDto.class);
        assertThat(method.getAnnotation(Transactional.class).isolation()).isEqualTo(Isolation.READ_COMMITTED);
    }

    private PracticeQuestion question(int order) {
        return PracticeQuestion.create(set, PracticePersistenceServiceTest.item(order), "[]", "[]", "[]");
    }
}
