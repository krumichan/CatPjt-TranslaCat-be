package jp.co.translacat.domain.languagelearning.practice.service;

import jp.co.translacat.domain.languagelearning.ai.dto.request.AiPracticeGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeReviewTargetDto;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.common.enums.ProfileSignalType;
import jp.co.translacat.domain.languagelearning.keyword.service.KeywordCandidateQueryService;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeAttempt;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeQuestion;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeSet;
import jp.co.translacat.domain.languagelearning.practice.entity.VocabularyMastery;
import jp.co.translacat.domain.languagelearning.practice.policy.PracticeComplexityPolicy;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeAttemptRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeSetRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.VocabularyMasteryRepository;
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileCommandService;
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileSignalService;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeGenerationServiceTest {
    @Mock private PracticeSetRepository setRepository;
    @Mock private PracticePersistenceService persistenceService;
    @Mock private PracticeComplexityPolicy complexityPolicy;
    @Mock private LanguageLearningUserSettingQueryService settingQueryService;
    @Mock private LearningProfileCommandService profileCommandService;
    @Mock private LearningProfileSignalService profileSignalService;
    @Mock private KeywordCandidateQueryService keywordCandidateQueryService;
    @Mock private VocabularyMasteryRepository masteryRepository;
    @Mock private PracticeAttemptRepository attemptRepository;
    @Mock private LanguageLearningUserSetting setting;
    @Mock private PracticeSet set;

    private PracticeGenerationService service;

    @BeforeEach
    void setUp() {
        service = new PracticeGenerationService(
                setRepository,
                persistenceService,
                complexityPolicy,
                settingQueryService,
                profileCommandService,
                profileSignalService,
                keywordCandidateQueryService,
                masteryRepository,
                attemptRepository
        );
    }

    @Test
    void readingStillCreatesFiveQuestionSetWithoutVocabularyReviewTargets() {
        LocalDate today = LocalDate.of(2026, 9, 13);
        when(settingQueryService.getOrCreateEntity(7L)).thenReturn(setting);
        when(settingQueryService.resolveToday(setting)).thenReturn(today);
        when(setting.getOriginLanguage()).thenReturn("ko");
        when(setting.getLearningLanguage()).thenReturn("ja");
        when(setRepository.findByUserIdAndLearningDateAndDomainAndMode(
                7L, today, PracticeDomain.READING, "COMPREHENSION"
        )).thenReturn(Optional.empty());
        when(complexityPolicy.resolve(
                7L, PracticeDomain.READING, "COMPREHENSION"
        )).thenReturn(4);
        when(complexityPolicy.mix(PracticeDomain.READING)).thenReturn(new int[]{1, 3, 1});
        when(keywordCandidateQueryService.findCandidates(7L, today)).thenReturn(List.of());
        when(attemptRepository
                .findTop30ByQuestionPracticeSetUserIdAndQuestionPracticeSetDomainAndCorrectFalseOrderBySubmittedAtDesc(
                        7L, PracticeDomain.READING
                )).thenReturn(List.of());
        when(persistenceService.createPending(any(), any())).thenReturn(set);

        assertThat(service.getOrGenerate(
                7L, PracticeDomain.READING, "COMPREHENSION"
        )).isSameAs(set);

        ArgumentCaptor<AiPracticeGenerationRequestDto> request =
                ArgumentCaptor.forClass(AiPracticeGenerationRequestDto.class);
        verify(persistenceService).createPending(org.mockito.ArgumentMatchers.eq(7L), request.capture());
        assertThat(request.getValue().mode()).isEqualTo("COMPREHENSION");
        assertThat(request.getValue().questionCount()).isEqualTo(5);
        assertThat(request.getValue().reviewTargets()).isEmpty();
        assertThat(request.getValue().reviewQuestionCount()).isZero();
        assertThat(List.of(
                request.getValue().easierCount(),
                request.getValue().currentCount(),
                request.getValue().challengeCount()
        )).containsExactly(1, 3, 1);
    }

    @Test
    void b4StructureChallengeIsDeferredBeforeSetCreation() {
        LocalDate today = LocalDate.of(2026, 9, 21);
        when(settingQueryService.getOrCreateEntity(7L)).thenReturn(setting);
        when(settingQueryService.resolveToday(setting)).thenReturn(today);
        when(setRepository.findByUserIdAndLearningDateAndDomainAndMode(
                7L, today, PracticeDomain.READING, "STRUCTURE")).thenReturn(Optional.empty());
        when(complexityPolicy.resolve(7L, PracticeDomain.READING, "STRUCTURE")).thenReturn(4);
        when(complexityPolicy.mix(PracticeDomain.READING)).thenReturn(new int[]{1, 3, 1});
        when(keywordCandidateQueryService.findCandidates(7L, today)).thenReturn(List.of());
        when(attemptRepository.findTop30ByQuestionPracticeSetUserIdAndQuestionPracticeSetDomainAndCorrectFalseOrderBySubmittedAtDesc(
                7L, PracticeDomain.READING)).thenReturn(List.of());
        assertThatThrownBy(() -> service.getOrGenerate(7L, PracticeDomain.READING, "STRUCTURE"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("READING_B5_STRUCTURE_DEFERRED"));
        verify(persistenceService, never()).createPending(any(), any());
    }

    @Test
    void availabilityUsesEachServerModeBandAndOnlyDefersB5StructureDemand() {
        LocalDate today = LocalDate.of(2026, 9, 21);
        when(settingQueryService.getOrCreateEntity(7L)).thenReturn(setting);
        when(settingQueryService.resolveToday(setting)).thenReturn(today);
        when(setting.getOriginLanguage()).thenReturn("ko");
        when(setting.getLearningLanguage()).thenReturn("ja");
        when(complexityPolicy.mix(PracticeDomain.READING)).thenReturn(new int[]{1, 3, 1});
        when(complexityPolicy.resolve(7L, PracticeDomain.READING, "COMPREHENSION")).thenReturn(5);
        when(complexityPolicy.resolve(7L, PracticeDomain.READING, "STRUCTURE")).thenReturn(4);
        when(complexityPolicy.resolve(7L, PracticeDomain.READING, "CONTEXT_INFERENCE")).thenReturn(5);

        var availability = service.availability(7L);

        assertThat(availability).extracting(item -> item.mode())
                .containsExactly("COMPREHENSION", "STRUCTURE", "CONTEXT_INFERENCE");
        assertThat(availability).extracting(item -> item.generationAvailable())
                .containsExactly(true, false, true);
        assertThat(availability).extracting(item -> item.reason())
                .containsExactly(null, "READING_B5_STRUCTURE_DEFERRED", null);
        verify(persistenceService, never()).createPending(any(), any());
    }

    @Test
    void allStandaloneVocabularyModesAreRetiredBeforeProfileOrDatabaseWork() {
        for (String legacy : List.of(
                "CONTEXTUAL_CHOICE", "MEANING_RELATION", "USAGE_DISTINCTION", "COMPOSITION"
        )) {
            assertThatThrownBy(() -> service.getOrGenerate(
                    7L, PracticeDomain.VOCABULARY, legacy
            )).isInstanceOfSatisfying(BusinessException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo("DAILY_VOCABULARY_RETIRED"));
        }
        org.mockito.Mockito.verifyNoInteractions(setRepository, persistenceService, settingQueryService,
                profileCommandService, keywordCandidateQueryService, masteryRepository);
    }

    @Test
    void legacyReviewTargetSelectionStillPreservesHistoricalIdentity() {
        PracticeAttempt repeatedWrong = mock(PracticeAttempt.class);
        PracticeQuestion wrongQuestion = mock(PracticeQuestion.class);
        VocabularyMastery lowMastery = mock(VocabularyMastery.class);
        when(repeatedWrong.getQuestion()).thenReturn(wrongQuestion);
        when(wrongQuestion.getCanonicalKey()).thenReturn("Recent-Key");
        when(wrongQuestion.getTargetExpression()).thenReturn("最近の誤り");
        when(wrongQuestion.getSkillTag()).thenReturn("NUANCE");
        when(wrongQuestion.getQuestionType()).thenReturn(
                jp.co.translacat.domain.languagelearning.common.enums.PracticeQuestionType.SINGLE_CHOICE
        );
        when(lowMastery.getCanonicalKey()).thenReturn("low-key");
        when(lowMastery.getDisplayExpression()).thenReturn("低い習熟度");
        when(lowMastery.getScore()).thenReturn(20.0);
        when(lowMastery.getEvaluationCount()).thenReturn(3);

        when(profileSignalService.getKeys(
                7L, ProfileSignalType.VOCABULARY_CANDIDATE, 12
        )).thenReturn(List.of("기타 후보"));
        when(attemptRepository
                .findTop30ByQuestionPracticeSetUserIdAndQuestionPracticeSetDomainAndCorrectFalseOrderBySubmittedAtDesc(
                        7L, PracticeDomain.VOCABULARY
                )).thenReturn(List.of(repeatedWrong, repeatedWrong));
        when(masteryRepository.findAllByUserIdOrderByScoreAsc(7L)).thenReturn(List.of(lowMastery));
        List<PracticeReviewTargetDto> targets = ReflectionTestUtils.invokeMethod(service, "reviewTargets", 7L);
        assertThat(targets).extracting(
                PracticeReviewTargetDto::canonicalKey,
                PracticeReviewTargetDto::expression
        ).startsWith(
                org.assertj.core.groups.Tuple.tuple("Recent-Key", "最近の誤り"),
                org.assertj.core.groups.Tuple.tuple("low-key", "低い習熟度")
        );
        assertThat(targets.getFirst().wrongCount()).isEqualTo(2);
        assertThat(targets.getFirst().preferredSkill())
                .isEqualTo("NUANCE");
        assertThat(targets.get(1).preferredSkill())
                .isEqualTo("MEANING");
    }
}
