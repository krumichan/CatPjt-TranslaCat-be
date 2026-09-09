package jp.co.translacat.domain.languagelearning.practice.service;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeSetStatus;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeSet;
import jp.co.translacat.domain.languagelearning.practice.enums.PracticeGenerationStatus;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeAttemptRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeMetricScoreRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeQuestionRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeSetRepository;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeQueryServiceTest {

    @Mock private PracticeSetRepository setRepository;
    @Mock private PracticeQuestionRepository questionRepository;
    @Mock private PracticeAttemptRepository attemptRepository;
    @Mock private PracticeMetricScoreRepository metricRepository;
    @Mock private LanguageLearningJsonCodec jsonCodec;
    @Mock private LanguageLearningUserSettingQueryService settingQueryService;
    @Mock private PracticeSet set;

    private PracticeQueryService service;

    @BeforeEach
    void setUp() {
        service = new PracticeQueryService(
                setRepository,
                questionRepository,
                attemptRepository,
                metricRepository,
                jsonCodec,
                settingQueryService
        );
    }

    @Test
    void returnsOnlyExistingTodaySetsWithoutGeneratingMissingModes() {
        LocalDate today = LocalDate.of(2026, 9, 7);
        when(settingQueryService.resolveToday(7L)).thenReturn(today);
        when(setRepository.findAllByUserIdAndLearningDateAndDomainOrderByIdAsc(
                7L, today, PracticeDomain.VOCABULARY
        )).thenReturn(List.of(set));
        when(set.getMode()).thenReturn("MEANING_RELATION");
        when(set.getId()).thenReturn(11L);
        when(set.getStatus()).thenReturn(PracticeSetStatus.COMPLETED);
        when(set.getQuestionCount()).thenReturn(10);
        when(set.getOfficialScore()).thenReturn(90.0);
        when(set.getGenerationStatus()).thenReturn(PracticeGenerationStatus.READY);
        when(questionRepository.countByPracticeSetId(11L)).thenReturn(10L);
        when(attemptRepository.countByQuestionPracticeSetIdAndAttemptNo(11L, 1))
                .thenReturn(10L);

        var result = service.getTodayStatus(7L, PracticeDomain.VOCABULARY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).mode()).isEqualTo("MEANING_RELATION");
        assertThat(result.get(0).status()).isEqualTo(PracticeSetStatus.COMPLETED);
        assertThat(result.get(0).answeredCount()).isEqualTo(10);
        assertThat(result.get(0).questionCount()).isEqualTo(10);
        assertThat(result.get(0).officialScore()).isEqualTo(90.0);
        assertThat(result.get(0).generatedQuestionCount()).isEqualTo(10);
        assertThat(result.get(0).generationStatus()).isEqualTo(PracticeGenerationStatus.READY);
    }

    @Test
    void pollingReturnsPartialGenerationWithoutDiscardingQuestionsOrRetrying() {
        when(setRepository.findByIdAndUserId(11L, 7L)).thenReturn(java.util.Optional.of(set));
        when(set.getId()).thenReturn(11L);
        when(set.getQuestionCount()).thenReturn(5);
        when(set.getGenerationStatus()).thenReturn(PracticeGenerationStatus.PARTIAL);
        when(set.getGenerationFailureMessage()).thenReturn("failed at three");

        var result = service.get(7L, 11L);

        assertThat(result.questionCount()).isEqualTo(5);
        assertThat(result.generatedQuestionCount()).isZero();
        assertThat(result.generationStatus()).isEqualTo(PracticeGenerationStatus.PARTIAL);
        assertThat(result.generationFailureMessage()).isEqualTo("failed at three");
        org.mockito.Mockito.verify(setRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }
}
