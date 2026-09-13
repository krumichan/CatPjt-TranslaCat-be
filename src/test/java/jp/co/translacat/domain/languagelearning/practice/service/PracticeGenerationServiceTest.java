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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    void createsOneContextualChoiceSetWithTenQuestionsAndAtMostTwoReviews() {
        LocalDate today = LocalDate.of(2026, 9, 13);
        when(settingQueryService.getOrCreateEntity(7L)).thenReturn(setting);
        when(settingQueryService.resolveToday(setting)).thenReturn(today);
        when(setting.getOriginLanguage()).thenReturn("ko");
        when(setting.getLearningLanguage()).thenReturn("ja");
        when(setRepository.findByUserIdAndLearningDateAndDomainAndMode(
                7L, today, PracticeDomain.VOCABULARY, "CONTEXTUAL_CHOICE"
        )).thenReturn(Optional.empty());
        when(complexityPolicy.resolve(
                7L, PracticeDomain.VOCABULARY, "CONTEXTUAL_CHOICE"
        )).thenReturn(4);
        when(complexityPolicy.mix(PracticeDomain.VOCABULARY)).thenReturn(new int[]{2, 6, 2});
        when(keywordCandidateQueryService.findCandidates(7L, today)).thenReturn(List.of());
        when(profileSignalService.getKeys(
                7L, ProfileSignalType.VOCABULARY_CANDIDATE, 12
        )).thenReturn(List.of("復習一", "復習二", "復習三"));
        when(attemptRepository
                .findTop30ByQuestionPracticeSetUserIdAndQuestionPracticeSetDomainAndCorrectFalseOrderBySubmittedAtDesc(
                        7L, PracticeDomain.VOCABULARY
                )).thenReturn(List.of());
        when(masteryRepository.findAllByUserIdOrderByScoreAsc(7L)).thenReturn(List.of());
        when(persistenceService.createPending(any(), any())).thenReturn(set);

        assertThat(service.getOrGenerate(
                7L, PracticeDomain.VOCABULARY, "CONTEXTUAL_CHOICE"
        )).isSameAs(set);

        ArgumentCaptor<AiPracticeGenerationRequestDto> request =
                ArgumentCaptor.forClass(AiPracticeGenerationRequestDto.class);
        verify(persistenceService).createPending(org.mockito.ArgumentMatchers.eq(7L), request.capture());
        assertThat(request.getValue().mode()).isEqualTo("CONTEXTUAL_CHOICE");
        assertThat(request.getValue().questionCount()).isEqualTo(10);
        assertThat(request.getValue().reviewTargets()).hasSize(3);
        assertThat(request.getValue().reviewQuestionCount()).isEqualTo(2);
        assertThat(request.getValue().reviewTargets())
                .extracting(PracticeReviewTargetDto::preferredSkill)
                .containsOnly("MEANING");
        assertThat(List.of(
                request.getValue().easierCount(),
                request.getValue().currentCount(),
                request.getValue().challengeCount()
        )).containsExactly(2, 6, 2);
    }

    @Test
    void legacyVocabularyModesCannotCreateNewDailySets() {
        for (String legacy : List.of(
                "MEANING_RELATION", "USAGE_DISTINCTION", "COMPOSITION"
        )) {
            assertThatThrownBy(() -> service.getOrGenerate(
                    7L, PracticeDomain.VOCABULARY, legacy
            )).isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void reviewTargetsPrioritizeRecentRepeatedWrongThenLowMasteryAndPreserveIdentity() {
        LocalDate today = LocalDate.of(2026, 9, 13);
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

        when(settingQueryService.getOrCreateEntity(7L)).thenReturn(setting);
        when(settingQueryService.resolveToday(setting)).thenReturn(today);
        when(setting.getOriginLanguage()).thenReturn("ko");
        when(setting.getLearningLanguage()).thenReturn("ja");
        when(setRepository.findByUserIdAndLearningDateAndDomainAndMode(
                7L, today, PracticeDomain.VOCABULARY, "CONTEXTUAL_CHOICE"
        )).thenReturn(Optional.empty());
        when(complexityPolicy.resolve(
                7L, PracticeDomain.VOCABULARY, "CONTEXTUAL_CHOICE"
        )).thenReturn(4);
        when(complexityPolicy.mix(PracticeDomain.VOCABULARY)).thenReturn(new int[]{2, 6, 2});
        when(keywordCandidateQueryService.findCandidates(7L, today)).thenReturn(List.of());
        when(profileSignalService.getKeys(
                7L, ProfileSignalType.VOCABULARY_CANDIDATE, 12
        )).thenReturn(List.of("기타 후보"));
        when(attemptRepository
                .findTop30ByQuestionPracticeSetUserIdAndQuestionPracticeSetDomainAndCorrectFalseOrderBySubmittedAtDesc(
                        7L, PracticeDomain.VOCABULARY
                )).thenReturn(List.of(repeatedWrong, repeatedWrong));
        when(masteryRepository.findAllByUserIdOrderByScoreAsc(7L)).thenReturn(List.of(lowMastery));
        when(persistenceService.createPending(any(), any())).thenReturn(set);

        service.getOrGenerate(7L, PracticeDomain.VOCABULARY, "CONTEXTUAL_CHOICE");

        ArgumentCaptor<AiPracticeGenerationRequestDto> request =
                ArgumentCaptor.forClass(AiPracticeGenerationRequestDto.class);
        verify(persistenceService).createPending(org.mockito.ArgumentMatchers.eq(7L), request.capture());
        assertThat(request.getValue().reviewQuestionCount()).isEqualTo(2);
        assertThat(request.getValue().reviewTargets()).extracting(
                PracticeReviewTargetDto::canonicalKey,
                PracticeReviewTargetDto::expression
        ).startsWith(
                org.assertj.core.groups.Tuple.tuple("Recent-Key", "最近の誤り"),
                org.assertj.core.groups.Tuple.tuple("low-key", "低い習熟度")
        );
        assertThat(request.getValue().reviewTargets().getFirst().wrongCount()).isEqualTo(2);
        assertThat(request.getValue().reviewTargets().getFirst().preferredSkill())
                .isEqualTo("NUANCE");
        assertThat(request.getValue().reviewTargets().get(1).preferredSkill())
                .isEqualTo("MEANING");
    }
}
