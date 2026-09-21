package jp.co.translacat.domain.languagelearning.practice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeGeneratedQuestionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeOptionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeReviewTargetDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.ReadingPassageBundleDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PersonalizedVocabularyPlanDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.VocabularyPlanItemDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiPracticeGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiPracticeGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeQuestionType;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeSetStatus;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeQuestion;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeSet;
import jp.co.translacat.domain.languagelearning.practice.entity.VocabularyMastery;
import jp.co.translacat.domain.languagelearning.practice.enums.PracticeGenerationStatus;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeQuestionRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeSetRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.VocabularyMasteryRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PracticePersistenceServiceTest {
    @Mock private PracticeSetRepository setRepository;
    @Mock private PracticeQuestionRepository questionRepository;
    @Mock private UserRepository userRepository;
    @Mock private VocabularyMasteryRepository masteryRepository;
    @Mock private User user;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final LanguageLearningJsonCodec jsonCodec = new LanguageLearningJsonCodec(objectMapper);
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 9, 12, 0);
    private PracticePersistenceService service;
    private PracticeSet set;

    @BeforeEach
    void setup() {
        service = new PracticePersistenceService(
                setRepository, questionRepository, userRepository, masteryRepository, jsonCodec
        );
        set = PracticeSet.create(user, now.toLocalDate(), PracticeDomain.READING,
                "COMPREHENSION", "ko", "ja", 5, 3);
        ReflectionTestUtils.setField(set, "id", 12L);
    }

    @Test
    void queuesRequestWithoutGeneratingAnyQuestion() {
        when(userRepository.findLockedById(7L)).thenReturn(Optional.of(user));
        when(setRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PracticeSet created = service.createPending(7L, request());

        assertThat(created.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.PENDING);
        assertThat(created.getQuestionCount()).isEqualTo(5);
        assertThat(created.getStatus()).isEqualTo(PracticeSetStatus.ACTIVE);
        assertThat(jsonCodec.read(created.getGenerationRequestJson(), AiPracticeGenerationRequestDto.class))
                .isEqualTo(request());
        verifyNoInteractions(questionRepository);
    }

    @Test
    void retiredVocabularyCannotCreatePendingEvenThroughPersistenceEntry() {
        assertThatThrownBy(() -> service.createPending(7L, contextualChoiceRequest()))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("DAILY_VOCABULARY_RETIRED"));
        verifyNoInteractions(userRepository, setRepository, questionRepository);
    }

    @Test
    void retiredVocabularyManualRetryPreservesIncompleteSetAndPlan() {
        PracticeSet retired = contextualChoiceSet();
        String snapshot = jsonCodec.write(contextualChoiceRequest(vocabularyPlan()));
        retired.queueGeneration(snapshot);
        retired.failGeneration("ORIGINAL_FAILURE", true);
        when(user.getId()).thenReturn(7L);
        when(setRepository.findLockedById(12L)).thenReturn(Optional.of(retired));

        assertThatThrownBy(() -> service.retry(7L, 12L)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo("DAILY_VOCABULARY_RETIRED"));
        assertThat(retired.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.PARTIAL);
        assertThat(retired.getStatus()).isEqualTo(PracticeSetStatus.ACTIVE);
        assertThat(retired.getGenerationRequestJson()).isEqualTo(snapshot);
        assertThat(retired.getGenerationFailureMessage()).isEqualTo("ORIGINAL_FAILURE");
        assertThat(retired.getOfficialScore()).isNull();
        verifyNoInteractions(questionRepository);
    }

    @Test
    void retiredDueVocabularyIsStoppedWithoutClaimOrDeletingAcceptedRows() {
        PracticeSet retired = contextualChoiceSet();
        String snapshot = jsonCodec.write(contextualChoiceRequest(vocabularyPlan()));
        retired.queueGeneration(snapshot);
        when(setRepository.findLockedById(12L)).thenReturn(Optional.of(retired));
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of(mock(PracticeQuestion.class)));

        assertThat(service.claim(12L, now, now.minusMinutes(30), 3)).isEmpty();
        assertThat(retired.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.PARTIAL);
        assertThat(retired.getGenerationFailureMessage()).isEqualTo("DAILY_VOCABULARY_RETIRED");
        assertThat(retired.getGenerationRequestJson()).isEqualTo(snapshot);
        assertThat(retired.getStatus()).isEqualTo(PracticeSetStatus.ACTIVE);
        assertThat(retired.getGenerationToken()).isNull();
        verify(questionRepository, never()).save(any());
        verify(questionRepository, never()).deleteAll();
    }

    @Test
    void b4StructureCannotCreateFreshSetIncludingChallengeB5() {
        when(userRepository.findLockedById(7L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.createPending(7L, ReadingB5AvailabilityTest.request(4, "STRUCTURE")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("READING_B5_STRUCTURE_DEFERRED"));
        verify(setRepository, never()).save(any());
        verifyNoInteractions(questionRepository);
    }

    @Test
    void existingB4PartialPreservesPublishedRowsAndRejectsManualRetry() {
        var original = ReadingB5AvailabilityTest.request(4, "STRUCTURE");
        PracticeSet structure = PracticeSet.create(user, now.toLocalDate(), PracticeDomain.READING,
                "STRUCTURE", "ko", "ja", 5, 4);
        ReflectionTestUtils.setField(structure, "id", 12L);
        String snapshot = jsonCodec.write(original);
        structure.queueGeneration(snapshot);
        structure.failGeneration("PREVIOUS_FAILURE", true);
        when(setRepository.findLockedById(12L)).thenReturn(Optional.of(structure));
        when(user.getId()).thenReturn(7L);
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of(question(1), question(2), question(3)));

        assertThatThrownBy(() -> service.retry(7L, 12L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("READING_B5_STRUCTURE_DEFERRED"));
        assertThat(structure.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.PARTIAL);
        assertThat(structure.getGenerationRequestJson()).isEqualTo(snapshot);
        assertThat(structure.getGenerationFailureMessage()).isEqualTo("PREVIOUS_FAILURE");
        verify(questionRepository, never()).save(any());
    }

    @Test
    void queuedLegacyB4RequestIsDeferredBeforeAnyProviderClaimAndFencesStaleLease() {
        var original = ReadingB5AvailabilityTest.request(4, "STRUCTURE");
        PracticeSet structure = PracticeSet.create(user, now.toLocalDate(), PracticeDomain.READING,
                "STRUCTURE", "ko", "ja", 5, 4);
        ReflectionTestUtils.setField(structure, "id", 12L);
        String snapshot = jsonCodec.write(original);
        structure.queueGeneration(snapshot);
        when(setRepository.findLockedById(12L)).thenReturn(Optional.of(structure));
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L)).thenReturn(List.of());

        assertThat(service.claim(12L, now, now.minusMinutes(30), 3)).isEmpty();
        assertThat(structure.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.FAILED);
        assertThat(structure.getGenerationFailureMessage()).isEqualTo("READING_B5_STRUCTURE_DEFERRED");
        assertThat(structure.getGenerationRequestJson()).isEqualTo(snapshot);
        verify(questionRepository, never()).save(any());

        structure.resumeGeneration();
        structure.claimGeneration("old-token", now.minusHours(1));
        assertThat(service.claim(12L, now, now.minusMinutes(30), 3)).isEmpty();
        assertThat(structure.ownsGeneration("old-token")).isFalse();
        assertThat(structure.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.FAILED);
        assertThat(structure.getGenerationRetryCount()).isZero();
    }

    @Test
    void inFlightB5ResultCannotPublishAfterPolicyBoundary() {
        var original = ReadingB5AvailabilityTest.request(4, "STRUCTURE");
        PracticeSet structure = PracticeSet.create(user, now.toLocalDate(), PracticeDomain.READING,
                "STRUCTURE", "ko", "ja", 5, 4);
        ReflectionTestUtils.setField(structure, "id", 12L);
        structure.queueGeneration(jsonCodec.write(original));
        structure.claimGeneration("old-token", now);
        var claim = new PracticePersistenceService.GenerationClaim(12L, 1, "old-token", 0,
                PracticePersistenceService.itemRequest(original, 1, List.of(), "old-token"));
        when(setRepository.findLockedById(12L)).thenReturn(Optional.of(structure));
        assertThat(service.persistReadingBundle(claim, mock(ReadingPassageBundleDto.class))).isFalse();
        assertThat(structure.ownsGeneration("old-token")).isFalse();
        assertThat(structure.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.FAILED);
        assertThat(structure.getGenerationFailureMessage()).isEqualTo("READING_B5_STRUCTURE_DEFERRED");
        assertThat(service.append(claim, response())).isFalse();
        verify(questionRepository, never()).save(any());
    }

    @Test
    void retirementDoesNotRevokeLiveLeaseButExpiredLeaseIsFenced() {
        PracticeSet retired = contextualChoiceSet();
        retired.queueGeneration(jsonCodec.write(contextualChoiceRequest(vocabularyPlan())));
        retired.claimGeneration("old-owned-token", now);
        when(setRepository.findLockedById(12L)).thenReturn(Optional.of(retired));
        assertThat(service.claim(12L, now, now.minusMinutes(30), 3)).isEmpty();
        assertThat(retired.ownsGeneration("old-owned-token")).isTrue();
        verifyNoInteractions(questionRepository);

        assertThat(service.claim(12L, now.plusHours(1), now.plusMinutes(30), 3)).isEmpty();
        assertThat(retired.ownsGeneration("old-owned-token")).isFalse();
        assertThat(retired.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.FAILED);
        assertThat(retired.getGenerationFailureMessage()).isEqualTo("DAILY_VOCABULARY_RETIRED");
        var stale = new PracticePersistenceService.GenerationClaim(12L, 1, "old-owned-token", 0,
                PracticePersistenceService.itemRequest(contextualChoiceRequest(), 1, List.of(), "old-owned-token"));
        assertThat(service.persistVocabularyPlan(stale, vocabularyPlan())).isFalse();
        assertThat(service.append(stale, contextualChoiceResponse(stale.request().requestId(), vocabularyPlan()))).isFalse();
        verify(questionRepository, never()).save(any());
    }

    @Test
    void concurrentCreationReturnsExistingSetAfterUserLock() {
        when(userRepository.findLockedById(7L)).thenReturn(Optional.of(user));
        when(setRepository.findByUserIdAndLearningDateAndDomainAndMode(
                7L, now.toLocalDate(), PracticeDomain.READING, "COMPREHENSION"
        )).thenReturn(Optional.of(set));

        assertThat(service.createPending(7L, request())).isSameAs(set);
        verify(setRepository, never()).save(any());
    }

    @Test
    void claimsFirstMissingSlotAndPassesExactStoredPrefix() {
        set.queueGeneration(jsonCodec.write(request()));
        lockSet();
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of(question(1), question(3)));

        var claim = service.claim(12L, now, now.minusMinutes(30), 3).orElseThrow();

        assertThat(claim.order()).isEqualTo(2);
        assertThat(claim.request().questionCount()).isEqualTo(1);
        assertThat(claim.request().previousQuestions()).extracting(PracticeGeneratedQuestionDto::order)
                .containsExactly(1);
        assertThat(claim.request().easierCount()).isEqualTo(1);
        assertThat(set.ownsGeneration(claim.token())).isTrue();
    }

    @Test
    void liveClaimCannotBeClaimedTwice() {
        set.queueGeneration(jsonCodec.write(request()));
        set.claimGeneration("active", now);
        lockSet();

        assertThat(service.claim(12L, now, now.minusMinutes(30), 3)).isEmpty();
        verifyNoInteractions(questionRepository);
    }

    @Test
    void expiredClaimIsFencedByDifferentToken() {
        set.queueGeneration(jsonCodec.write(request()));
        set.claimGeneration("expired", now.minusHours(1));
        lockSet();

        var claim = service.claim(12L, now, now.minusMinutes(30), 3).orElseThrow();

        assertThat(claim.token()).isNotEqualTo("expired");
        assertThat(set.ownsGeneration("expired")).isFalse();
        assertThat(set.ownsGeneration(claim.token())).isTrue();
        assertThat(set.getGenerationRetryCount()).isEqualTo(1);
    }

    @Test
    void exhaustedLeaseRecoveryBudgetBecomesTerminalAndDoesNotCreateAnotherClaim() {
        set.queueGeneration(jsonCodec.write(request()));
        ReflectionTestUtils.setField(set, "generationRetryCount", 3);
        set.claimGeneration("expired", now.minusHours(1));
        lockSet();
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of(question(1)));

        assertThat(service.claim(12L, now, now.minusMinutes(30), 3)).isEmpty();

        assertThat(set.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.PARTIAL);
        assertThat(set.getGenerationFailureMessage())
                .isEqualTo(PracticePersistenceService.LEASE_RECOVERY_EXHAUSTED);
        assertThat(set.ownsGeneration("expired")).isFalse();
    }

    @Test
    void failedAndPartialManualRetriesCreateFreshAttemptIdentity() {
        when(user.getId()).thenReturn(7L);
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of());

        for (boolean hasQuestions : List.of(false, true)) {
            set = PracticeSet.create(user, now.toLocalDate(), PracticeDomain.READING,
                    "COMPREHENSION", "ko", "ja", 5, 3);
            ReflectionTestUtils.setField(set, "id", 12L);
            set.queueGeneration(jsonCodec.write(request()));
            set.failGeneration("initial", hasQuestions);
            lockSet();

            service.retry(7L, 12L);
            var first = service.claim(12L, now, now.minusMinutes(30), 3).orElseThrow();
            set.failGeneration("again", hasQuestions);
            service.retry(7L, 12L);
            var second = service.claim(12L, now.plusSeconds(1), now.minusMinutes(30), 3).orElseThrow();

            assertThat(first.token()).isNotEqualTo(second.token());
            assertThat(first.request().requestId()).isNotEqualTo(second.request().requestId());
            assertThat(first.request().requestId()).isNotEqualTo(request().requestId());
            assertThat(second.request().requestId()).isNotEqualTo(request().requestId());
        }
        verify(questionRepository, never()).deleteAll();
    }

    @Test
    void appendPublishesOnlyOneQuestionAndQueuesNextSlot() {
        set.claimGeneration("active", now);
        lockSet();
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L)).thenReturn(List.of(question(1)));

        assertThat(service.append(claim(2, "active"), response())).isTrue();

        ArgumentCaptor<PracticeQuestion> saved = ArgumentCaptor.forClass(PracticeQuestion.class);
        verify(questionRepository).save(saved.capture());
        assertThat(saved.getValue().getOrderNo()).isEqualTo(2);
        assertThat(set.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.PENDING);
        assertThat(set.getStatus()).isEqualTo(PracticeSetStatus.ACTIVE);
    }

    @Test
    void verifiedReadingPassagePublishesThreeRowsAtomicallyAndNextClaimStartsAtFour() {
        set.queueGeneration(jsonCodec.write(request()));
        lockSet();
        var persisted = new ArrayList<PracticeQuestion>();
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenAnswer(invocation -> List.copyOf(persisted));
        when(questionRepository.save(any())).thenAnswer(invocation -> {
            PracticeQuestion saved = invocation.getArgument(0);
            persisted.add(saved);
            return saved;
        });

        var first = service.claim(12L, now, now.minusMinutes(30), 3).orElseThrow();
        var bundle = new ReadingPassageBundleDto("p1", "practice", "private-hash",
                List.of(), List.of(item(1), item(2), item(3)));
        assertThat(service.persistReadingBundle(first, bundle)).isTrue();
        assertThat(jsonCodec.read(set.getGenerationRequestJson(), AiPracticeGenerationRequestDto.class)
                .readingBundles()).containsEntry("p1", bundle);
        assertThat(persisted).isEmpty();
        var response = new AiPracticeGenerationResponseDto(first.request().requestId(), "practice",
                PracticeDomain.READING, "COMPREHENSION", 3, bundle.questions(), null, bundle);
        assertThat(service.append(first, response)).isTrue();
        assertThat(persisted).extracting(PracticeQuestion::getOrderNo).containsExactly(1, 2, 3);
        assertThat(set.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.PENDING);
        var second = service.claim(12L, now.plusSeconds(1), now.minusMinutes(30), 3).orElseThrow();

        assertThat(set.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.GENERATING);
        assertThat(second.order()).isEqualTo(4);
        assertThat(second.token()).isNotEqualTo(first.token());
        assertThat(second.request().requestId()).isNotEqualTo(first.request().requestId());
        assertThat(second.request().previousQuestions())
                .extracting(PracticeGeneratedQuestionDto::order)
                .containsExactly(1, 2, 3);
        assertThat(second.request().readingBundles()).containsEntry("p1", bundle);
        assertThat(second.request().questionCount()).isEqualTo(2);
    }

    @Test
    void expiredClaimAfterPrivateReadingBundleSnapshotReplaysWholePassage() {
        set.queueGeneration(jsonCodec.write(request()));
        lockSet();
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of());
        var initial = service.claim(12L, now, now.minusMinutes(30), 3).orElseThrow();
        var bundle = new ReadingPassageBundleDto("p1", "practice", "private-hash",
                List.of(), List.of(item(1), item(2), item(3)));
        assertThat(service.persistReadingBundle(initial, bundle)).isTrue();
        verify(questionRepository, never()).save(any());

        var replay = service.claim(12L, now.plusHours(1), now.plusMinutes(30), 3).orElseThrow();
        assertThat(replay.order()).isEqualTo(1);
        assertThat(replay.request().questionCount()).isEqualTo(3);
        assertThat(replay.request().readingBundles()).containsEntry("p1", bundle);
        assertThat(replay.token()).isNotEqualTo(initial.token());
        assertThat(replay.request().requestId()).isNotEqualTo(initial.request().requestId());
    }

    @Test
    void secondReadingPassagePublishesTwoRowsAtomicallyAndFinishes() {
        set.queueGeneration(jsonCodec.write(request()));
        set.claimGeneration("active", now);
        lockSet();
        var prefix = List.of(question(1), question(2), question(3));
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L)).thenReturn(prefix);
        var original = request();
        var fourth = PracticePersistenceService.itemRequest(original, 4,
                List.of(item(1), item(2), item(3)), "active");
        var claim = new PracticePersistenceService.GenerationClaim(12L, 4, "active", 0, fourth);
        var fourthItem = new PracticeGeneratedQuestionDto(1, PracticeQuestionType.SINGLE_CHOICE,
                PracticeDifficulty.EASIER, 3, "p2", "別の本文です。", "質問です。",
                List.of(new PracticeOptionDto("A", "はい")), List.of("A"), "MAIN_IDEA",
                "本文", "설명", "説明", null, null, false, List.of());
        var fifthItem = new PracticeGeneratedQuestionDto(2, PracticeQuestionType.SINGLE_CHOICE,
                PracticeDifficulty.EASIER, 3, "p2", "別の本文です。", "質問です。",
                List.of(new PracticeOptionDto("A", "はい")), List.of("A"), "MAIN_IDEA",
                "本文", "설명", "説明", null, null, false, List.of());
        var bundle = new ReadingPassageBundleDto("p2", "practice", "private-hash",
                List.of(), List.of(fourthItem, fifthItem));
        var stored = new AiPracticeGenerationRequestDto(original.requestId(), original.domain(),
                original.mode(), original.originLanguage(), original.learningLanguage(), 5,
                original.complexityBand(), original.easierCount(), original.currentCount(),
                original.challengeCount(), original.selectedKeywords(), original.weakSignals(),
                original.recentMistakes(), original.reviewTargets(), original.reviewQuestionCount(),
                original.generationDate(), original.previousQuestions(), null, false,
                java.util.Map.of("p2", bundle), null);
        set.updateGenerationRequest(jsonCodec.write(stored));
        var generated = new AiPracticeGenerationResponseDto(fourth.requestId(), "practice",
                PracticeDomain.READING, "COMPREHENSION", 3, bundle.questions(), null, bundle);

        assertThat(service.append(claim, generated)).isFalse();
        ArgumentCaptor<PracticeQuestion> captured = ArgumentCaptor.forClass(PracticeQuestion.class);
        verify(questionRepository, times(2)).save(captured.capture());
        assertThat(captured.getAllValues()).extracting(PracticeQuestion::getOrderNo)
                .containsExactly(4, 5);
        assertThat(set.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.READY);
    }

    @Test
    void staleReadingClaimCannotPersistOrPublishItsBundle() {
        set.queueGeneration(jsonCodec.write(request()));
        set.claimGeneration("new-owner", now);
        lockSet();
        var stale = new PracticePersistenceService.GenerationClaim(12L, 1, "expired-owner", 0,
                PracticePersistenceService.itemRequest(request(), 1, List.of(), "expired-owner"));
        var bundle = new ReadingPassageBundleDto("p1", "practice", "hash", List.of(), List.of());
        String snapshot = set.getGenerationRequestJson();

        assertThat(service.persistReadingBundle(stale, bundle)).isFalse();
        assertThat(service.append(stale, response())).isFalse();
        assertThat(set.getGenerationRequestJson()).isEqualTo(snapshot);
        verifyNoInteractions(questionRepository);
    }

    @Test
    void legacyReplayContextualChoicePlanIsDurableBeforeFirstQuestionAndReusedByLeaseRetry() {
        PracticeSet vocabularySet = contextualChoiceSet();
        vocabularySet.queueGeneration(jsonCodec.write(contextualChoiceRequest()));
        when(setRepository.findLockedById(12L)).thenReturn(Optional.of(vocabularySet));
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of(), List.of());

        var first = legacyReplayClaim(vocabularySet, now).orElseThrow();
        assertThat(first.request().vocabularyPlan()).isNull();
        assertThat(service.persistVocabularyPlan(first, vocabularyPlan())).isTrue();

        AiPracticeGenerationRequestDto persisted = jsonCodec.read(
                vocabularySet.getGenerationRequestJson(), AiPracticeGenerationRequestDto.class
        );
        assertThat(persisted.vocabularyPlan()).isEqualTo(vocabularyPlan());
        assertThat(vocabularySet.ownsGeneration(first.token())).isTrue();
        verify(questionRepository, never()).save(any());

        var recovered = legacyReplayClaim(vocabularySet, now.plusHours(1)).orElseThrow();
        assertThat(recovered.order()).isEqualTo(1);
        assertThat(recovered.token()).isNotEqualTo(first.token());
        assertThat(recovered.request().vocabularyPlan()).isEqualTo(vocabularyPlan());
        assertThat(recovered.request().requestId()).isNotEqualTo(first.request().requestId());
    }

    @Test
    void legacyReplayContextualChoicePlanAndAcceptedPrefixAreReusedForNextOrder() {
        PracticeSet vocabularySet = contextualChoiceSet();
        vocabularySet.queueGeneration(jsonCodec.write(contextualChoiceRequest()));
        when(setRepository.findLockedById(12L)).thenReturn(Optional.of(vocabularySet));
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of(), List.of(), List.of());
        when(user.getId()).thenReturn(7L);
        var mastery = mock(jp.co.translacat.domain.languagelearning.practice.entity.VocabularyMastery.class);
        when(masteryRepository.findByUserIdAndCanonicalKey(7L, "追加の検証期間"))
                .thenReturn(Optional.of(mastery));
        when(questionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var first = legacyReplayClaim(vocabularySet, now).orElseThrow();
        var generated = contextualChoiceResponse(first.request().requestId(), vocabularyPlan());
        assertThat(service.persistVocabularyPlan(first, generated.vocabularyPlan())).isTrue();
        assertThat(service.append(first, generated)).isTrue();

        ArgumentCaptor<PracticeQuestion> saved = ArgumentCaptor.forClass(PracticeQuestion.class);
        verify(questionRepository).save(saved.capture());
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of(saved.getValue()));
        var second = legacyReplayClaim(vocabularySet, now.plusSeconds(1)).orElseThrow();

        assertThat(second.order()).isEqualTo(2);
        assertThat(second.request().previousQuestions()).hasSize(1);
        assertThat(second.request().vocabularyPlan()).isEqualTo(vocabularyPlan());
        assertThat(second.request().reviewQuestionCount()).isEqualTo(1);
        assertThat(second.request().reviewTargets().getFirst().canonicalKey())
                .isEqualTo("顧客サービスへの影響");
    }

    @Test
    void staleClaimCannotPersistOrReplaceVocabularyPlan() {
        PracticeSet vocabularySet = contextualChoiceSet();
        vocabularySet.queueGeneration(jsonCodec.write(contextualChoiceRequest()));
        vocabularySet.claimGeneration("replacement", now);
        when(setRepository.findLockedById(12L)).thenReturn(Optional.of(vocabularySet));
        var expired = new PracticePersistenceService.GenerationClaim(
                12L, 1, "expired", 0,
                PracticePersistenceService.itemRequest(
                        contextualChoiceRequest(), 1, List.of(), "expired"
                )
        );

        assertThat(service.persistVocabularyPlan(expired, vocabularyPlan())).isFalse();
        assertThat(jsonCodec.read(
                vocabularySet.getGenerationRequestJson(), AiPracticeGenerationRequestDto.class
        ).vocabularyPlan()).isNull();
        assertThat(vocabularySet.ownsGeneration("replacement")).isTrue();
    }

    @Test
    void legacyReplayPersistedVocabularyPlanCannotBeMutatedByLaterAttempt() {
        PracticeSet vocabularySet = contextualChoiceSet();
        AiPracticeGenerationRequestDto withPlan = contextualChoiceRequest(vocabularyPlan());
        vocabularySet.queueGeneration(jsonCodec.write(withPlan));
        when(setRepository.findLockedById(12L)).thenReturn(Optional.of(vocabularySet));
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L)).thenReturn(List.of());
        var claim = legacyReplayClaim(vocabularySet, now).orElseThrow();
        var mutated = new PersonalizedVocabularyPlanDto("unexpected-version", vocabularyPlan().items());

        assertThatThrownBy(() -> service.persistVocabularyPlan(claim, mutated))
                .isInstanceOf(BusinessException.class);
        assertThat(jsonCodec.read(
                vocabularySet.getGenerationRequestJson(), AiPracticeGenerationRequestDto.class
        ).vocabularyPlan()).isEqualTo(vocabularyPlan());
    }

    @Test
    void legacyReplayCurrentUnacceptedReviewDistractorPatchAndQuestionArePublishedTogether() {
        var originalPlan = vocabularyPlan();
        var originalItem = originalPlan.items().getFirst();
        var repairedItem = new VocabularyPlanItemDto(
                originalItem.globalOrder(), originalItem.reviewTarget(),
                originalItem.targetExpression(), originalItem.canonicalKey(),
                List.of("修正候補一", "修正候補二", "修正候補三"),
                originalItem.skillTag(), originalItem.difficulty(), originalItem.complexityBand(),
                originalItem.scenarioFamily(), originalItem.anchorType(), originalItem.anchorValue()
        );
        var repairedPlan = withPlanItem(originalPlan, 1, repairedItem);
        PracticeSet vocabularySet = contextualChoiceSet();
        vocabularySet.queueGeneration(jsonCodec.write(contextualChoiceRequest(originalPlan)));
        when(setRepository.findLockedById(12L)).thenReturn(Optional.of(vocabularySet));
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of(), List.of());
        when(user.getId()).thenReturn(7L);
        var mastery = mock(VocabularyMastery.class);
        when(masteryRepository.findByUserIdAndCanonicalKey(7L, originalItem.canonicalKey()))
                .thenReturn(Optional.of(mastery));
        when(questionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var first = legacyReplayClaim(vocabularySet, now).orElseThrow();
        var generated = contextualChoiceResponse(first.request().requestId(), repairedPlan);
        assertThat(service.append(first, generated)).isTrue();
        var stored = jsonCodec.read(vocabularySet.getGenerationRequestJson(),
                AiPracticeGenerationRequestDto.class);
        assertThat(stored.vocabularyPlan()).isEqualTo(repairedPlan);
        ArgumentCaptor<PracticeQuestion> saved = ArgumentCaptor.forClass(PracticeQuestion.class);
        verify(questionRepository).save(saved.capture());
        assertThat(saved.getValue().getOptionsJson()).contains("修正候補一");

        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of(saved.getValue()));
        var second = legacyReplayClaim(vocabularySet, now.plusSeconds(1)).orElseThrow();
        assertThat(second.request().vocabularyPlan()).isEqualTo(repairedPlan);
        assertThat(second.request().previousQuestions()).hasSize(1);
        assertThat(second.request().previousQuestions().getFirst().options())
                .extracting(PracticeOptionDto::text).contains("修正候補一");
    }

    @Test
    void staleClaimCannotPatchCurrentPlanOrAppendQuestion() {
        var originalPlan = vocabularyPlan();
        var originalItem = originalPlan.items().getFirst();
        var repairedPlan = withPlanItem(originalPlan, 1, new VocabularyPlanItemDto(
                originalItem.globalOrder(), originalItem.reviewTarget(),
                originalItem.targetExpression(), originalItem.canonicalKey(),
                List.of("修正候補一", "修正候補二", "修正候補三"),
                originalItem.skillTag(), originalItem.difficulty(), originalItem.complexityBand(),
                originalItem.scenarioFamily(), originalItem.anchorType(), originalItem.anchorValue()
        ));
        PracticeSet vocabularySet = contextualChoiceSet();
        vocabularySet.queueGeneration(jsonCodec.write(contextualChoiceRequest(originalPlan)));
        vocabularySet.claimGeneration("new-owner", now);
        when(setRepository.findLockedById(12L)).thenReturn(Optional.of(vocabularySet));
        var stale = new PracticePersistenceService.GenerationClaim(
                12L, 1, "old-owner", 0,
                PracticePersistenceService.itemRequest(
                        contextualChoiceRequest(originalPlan), 1, List.of(), "old-owner")
        );
        assertThat(service.append(stale,
                contextualChoiceResponse(stale.request().requestId(), repairedPlan))).isFalse();
        assertThat(jsonCodec.read(vocabularySet.getGenerationRequestJson(),
                AiPracticeGenerationRequestDto.class).vocabularyPlan()).isEqualTo(originalPlan);
        verify(questionRepository, never()).save(any());
    }

    @Test
    void legacyReplayCorruptedPersistedPlanAndAcceptedQuestionFailBeforeNextAiCall() {
        var plan = vocabularyPlan();
        var first = contextualChoiceItem(1, PracticeDifficulty.CURRENT, 4, "MEANING",
                "追加の検証期間", "追加の検証期間", "A");
        var firstItem = plan.items().getFirst();
        var corruptedPlan = withPlanItem(plan, 1, new VocabularyPlanItemDto(
                firstItem.globalOrder(), firstItem.reviewTarget(), firstItem.targetExpression(),
                firstItem.canonicalKey(), List.of("不整合一", "不整合二", "不整合三"),
                firstItem.skillTag(), firstItem.difficulty(), firstItem.complexityBand(),
                firstItem.scenarioFamily(), firstItem.anchorType(), firstItem.anchorValue()
        ));
        PracticeSet vocabularySet = contextualChoiceSet();
        vocabularySet.queueGeneration(jsonCodec.write(contextualChoiceRequest(corruptedPlan)));
        when(setRepository.findLockedById(12L)).thenReturn(Optional.of(vocabularySet));
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of(persistedQuestion(vocabularySet, first)));

        assertThat(legacyReplayClaim(vocabularySet, now)).isEmpty();
        assertThat(vocabularySet.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.PARTIAL);
        assertThat(vocabularySet.getGenerationFailureMessage()).isEqualTo("AI_SCHEMA_INVALID");
    }

    @Test
    void legacyReplayLiteralNullStoredRequestIsExplicitTerminalFailure() {
        PracticeSet vocabularySet = contextualChoiceSet();
        vocabularySet.queueGeneration("null");
        when(setRepository.findLockedById(12L)).thenReturn(Optional.of(vocabularySet));
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of());

        assertThat(legacyReplayClaim(vocabularySet, now)).isEmpty();
        assertThat(vocabularySet.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.FAILED);
        assertThat(vocabularySet.getGenerationFailureMessage()).isEqualTo("STORED_REQUEST_INVALID");
    }

    @Test
    void onlyCurrentNewLexicalBundleMayChangeInReturnedPlan() {
        var plan = vocabularyPlan();
        var prefix = List.of(
                contextualChoiceItem(1, PracticeDifficulty.CURRENT, 4, "MEANING",
                        "追加の検証期間", "追加の検証期間", "A"),
                contextualChoiceItem(2, PracticeDifficulty.EASIER, 3, "NUANCE",
                        "顧客サービスへの影響", "顧客サービスへの影響", "B")
        );
        var third = plan.items().get(2);
        var request = PracticePersistenceService.itemRequest(
                contextualChoiceRequest(plan), 3, prefix, "third-token"
        );
        var repaired = withPlanItem(plan, 3, new VocabularyPlanItemDto(
                third.globalOrder(), third.reviewTarget(), "新しい個別表現", "新しい個別表現",
                List.of("別の候補一", "別の候補二", "別の候補三"),
                third.skillTag(), third.difficulty(), third.complexityBand(),
                third.scenarioFamily(), third.anchorType(), third.anchorValue()
        ));
        PracticeGenerationWorker.validateContextualChoicePlanDelta(request, repaired);
        assertThatThrownBy(() -> PracticeGenerationWorker.validateContextualChoicePlanDelta(
                request, withPlanItem(repaired, 1, repaired.items().get(2))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PracticeGenerationWorker.validateContextualChoicePlanDelta(
                request, withPlanItem(repaired, 4, repaired.items().get(2))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PracticeGenerationWorker.validateContextualChoicePlanDelta(
                request, withPlanItem(plan, 3, new VocabularyPlanItemDto(
                        third.globalOrder(), third.reviewTarget(), third.targetExpression(),
                        third.canonicalKey(), third.distractors(), "REGISTER", third.difficulty(),
                        third.complexityBand(), third.scenarioFamily(),
                        third.anchorType(), third.anchorValue()
                )))).isInstanceOf(BusinessException.class);
        List<VocabularyPlanItemDto> forbiddenMetadata = List.of(
                new VocabularyPlanItemDto(3, false, third.targetExpression(), third.canonicalKey(),
                        third.distractors(), third.skillTag(), PracticeDifficulty.EASIER,
                        third.complexityBand(), third.scenarioFamily(), third.anchorType(), third.anchorValue()),
                new VocabularyPlanItemDto(3, false, third.targetExpression(), third.canonicalKey(),
                        third.distractors(), third.skillTag(), third.difficulty(),
                        5, third.scenarioFamily(), third.anchorType(), third.anchorValue()),
                new VocabularyPlanItemDto(3, false, third.targetExpression(), third.canonicalKey(),
                        third.distractors(), third.skillTag(), third.difficulty(),
                        third.complexityBand(), "OTHER_SCENARIO", third.anchorType(), third.anchorValue()),
                new VocabularyPlanItemDto(3, false, third.targetExpression(), third.canonicalKey(),
                        third.distractors(), third.skillTag(), third.difficulty(),
                        third.complexityBand(), third.scenarioFamily(), "WEAK_SIGNAL", third.anchorValue()),
                new VocabularyPlanItemDto(3, false, third.targetExpression(), third.canonicalKey(),
                        third.distractors(), third.skillTag(), third.difficulty(),
                        third.complexityBand(), third.scenarioFamily(), third.anchorType(), "別のアンカー")
        );
        for (var mutation : forbiddenMetadata) {
            assertThatThrownBy(() -> PracticeGenerationWorker.validateContextualChoicePlanDelta(
                    request, withPlanItem(plan, 3, mutation)
            )).isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void currentReviewTargetAndCanonicalRemainImmutableDuringDistractorPatch() {
        var plan = vocabularyPlan();
        var first = plan.items().getFirst();
        var request = PracticePersistenceService.itemRequest(
                contextualChoiceRequest(plan), 1, List.of(), "review-token"
        );
        var distractorPatch = withPlanItem(plan, 1, new VocabularyPlanItemDto(
                1, true, first.targetExpression(), first.canonicalKey(),
                List.of("別候補一", "別候補二", "別候補三"), first.skillTag(),
                first.difficulty(), first.complexityBand(), null, null, null
        ));
        PracticeGenerationWorker.validateContextualChoicePlanDelta(request, distractorPatch);
        assertThatThrownBy(() -> PracticeGenerationWorker.validateContextualChoicePlanDelta(
                request, withPlanItem(plan, 1, new VocabularyPlanItemDto(
                        1, true, "別の復習対象", first.canonicalKey(), first.distractors(),
                        first.skillTag(), first.difficulty(), first.complexityBand(), null, null, null
                )))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PracticeGenerationWorker.validateContextualChoicePlanDelta(
                request, withPlanItem(plan, 1, new VocabularyPlanItemDto(
                        1, true, first.targetExpression(), "別のcanonical", first.distractors(),
                        first.skillTag(), first.difficulty(), first.complexityBand(), null, null, null
                )))).isInstanceOf(BusinessException.class);
    }

    @Test
    void lastAppendFinishesGenerationButNotLearning() {
        set.claimGeneration("active", now);
        lockSet();
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of(question(1), question(2), question(3), question(4)));

        assertThat(service.append(claim(5, "active"), response())).isFalse();

        assertThat(set.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.READY);
        assertThat(set.getStatus()).isEqualTo(PracticeSetStatus.ACTIVE);
        assertThat(set.getCompletedAt()).isNull();
    }

    @Test
    void staleSuccessAndFailureCannotOverwriteRetriedGeneration() {
        set.claimGeneration("replacement", now);
        lockSet();

        assertThat(service.append(claim(1, "expired"), response())).isFalse();
        service.fail(claim(1, "expired"), "late error");

        assertThat(set.ownsGeneration("replacement")).isTrue();
        assertThat(set.getGenerationFailureMessage()).isNull();
        verifyNoInteractions(questionRepository);
    }

    @Test
    void fillingGapRejectsExpressionAlreadyPresentInRetainedLaterQuestion() {
        set = PracticeSet.create(user, now.toLocalDate(), PracticeDomain.VOCABULARY,
                "MEANING_RELATION", "ko", "ja", 10, 3);
        ReflectionTestUtils.setField(set, "id", 12L);
        set.claimGeneration("active", now);
        lockSet();
        var later = mock(PracticeQuestion.class);
        when(later.getOrderNo()).thenReturn(3);
        when(later.getTargetExpression()).thenReturn("ＫＥＹ");
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of(question(1), later));
        var duplicate = new PracticeGeneratedQuestionDto(1, PracticeQuestionType.SINGLE_CHOICE,
                PracticeDifficulty.CURRENT, 3, null, null, "問題です。", List.of(new PracticeOptionDto("A", "はい")),
                List.of("A"), "MEANING", null, "설명", "説明", "key", "other", false, List.of());
        var generated = new AiPracticeGenerationResponseDto("request", "practice", PracticeDomain.VOCABULARY,
                "MEANING_RELATION", 3, List.of(duplicate));

        assertThatThrownBy(() -> service.append(claim(2, "active"), generated))
                .isInstanceOf(BusinessException.class);
        verify(questionRepository, never()).save(any());
    }

    @Test
    void failurePreservesPartialQuestionsAndReason() {
        set.claimGeneration("active", now);
        lockSet();
        when(questionRepository.countByPracticeSetId(12L)).thenReturn(2L);

        service.fail(claim(3, "active"), "generation unavailable");

        assertThat(set.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.PARTIAL);
        assertThat(set.getGenerationFailureMessage()).isEqualTo("generation unavailable");
        verify(questionRepository, never()).deleteAll();
        verify(questionRepository, never()).save(any());
    }

    @Test
    void failureWithoutQuestionsBecomesFailed() {
        set.claimGeneration("active", now);
        lockSet();

        service.fail(claim(1, "active"), "generation unavailable");

        assertThat(set.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.FAILED);
    }

    @Test
    void transientFailureIsDeferredDurablyAndNotClaimedBeforeAvailableAt() {
        set.queueGeneration(jsonCodec.write(request()));
        set.claimGeneration("active", now);
        lockSet();
        when(questionRepository.countByPracticeSetId(12L)).thenReturn(0L);

        service.recordInfrastructureFailure(
                claim(1, "active"), "CIRCUIT_OPEN", now.plusSeconds(10), 3
        );

        assertThat(set.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.PENDING);
        assertThat(set.getGenerationAvailableAt()).isEqualTo(now.plusSeconds(10));
        assertThat(set.getGenerationRetryCount()).isEqualTo(1);
        assertThat(set.getGenerationFailureMessage()).isNull();

        assertThat(service.claim(12L, now.plusSeconds(9), now.minusMinutes(30), 3)).isEmpty();
        verify(questionRepository, never()).deleteAll();
    }

    @Test
    void exhaustedInfrastructureRetryBudgetBecomesTerminalWithoutDeletingRows() {
        set.queueGeneration(jsonCodec.write(request()));
        ReflectionTestUtils.setField(set, "generationRetryCount", 3);
        set.claimGeneration("active", now);
        lockSet();
        when(questionRepository.countByPracticeSetId(12L)).thenReturn(2L);

        service.recordInfrastructureFailure(
                claim(3, "active"), "HTTP_5XX", now.plusMinutes(1), 3
        );

        assertThat(set.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.PARTIAL);
        assertThat(set.getGenerationFailureMessage()).isEqualTo("HTTP_5XX");
        assertThat(set.getGenerationAvailableAt()).isNull();
        verify(questionRepository, never()).deleteAll();
    }

    @Test
    void malformedStoredRequestFailsWithExplicitSafeClassification() {
        set.queueGeneration("not-json");
        lockSet();
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of());

        assertThat(service.claim(12L, now, now.minusMinutes(30), 3)).isEmpty();

        assertThat(set.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.FAILED);
        assertThat(set.getGenerationFailureMessage())
                .isEqualTo(PracticePersistenceService.STORED_REQUEST_INVALID);
        verifyNoInteractions(masteryRepository);
    }

    @Test
    void repeatedRetryDoesNotResetRunningWorkOrDeleteQuestions() {
        set.failGeneration("failed at three", true);
        when(user.getId()).thenReturn(7L);
        lockSet();

        service.retry(7L, 12L);
        assertThat(set.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.PENDING);
        set.claimGeneration("retry", now);
        service.retry(7L, 12L);

        assertThat(set.ownsGeneration("retry")).isTrue();
        verifyNoInteractions(questionRepository);
    }

    @Test
    void retryRequiresOwnership() {
        when(user.getId()).thenReturn(7L);
        lockSet();

        assertThatThrownBy(() -> service.retry(8L, 12L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(questionRepository);
    }

    @Test
    void readingPassageRequestsKeepGlobalDifficultyMixAndReviewSelection() {
        var base = request();
        var first = PracticePersistenceService.itemRequest(base, 1, List.of(), "token");
        var fourth = PracticePersistenceService.itemRequest(base, 4, List.of(), "token");
        assertThat(first.questionCount()).isEqualTo(3);
        assertThat(fourth.questionCount()).isEqualTo(2);
        assertThat(first.easierCount() + fourth.easierCount()).isEqualTo(1);
        assertThat(first.currentCount() + fourth.currentCount()).isEqualTo(3);
        assertThat(first.challengeCount() + fourth.challengeCount()).isEqualTo(1);
        assertThat(first.readingSlotTargets()).hasSize(5);
        assertThat(fourth.readingSlotTargets()).isEqualTo(first.readingSlotTargets());
        var legacyThird = PracticePersistenceService.itemRequest(base, 3,
                List.of(item(1), item(2)), "legacy-token");
        assertThat(legacyThird.questionCount()).isEqualTo(1);
        assertThat(legacyThird.previousQuestions()).hasSize(2);
        assertThat(fourth.previousQuestions()).isEmpty();
        var vocabulary = new AiPracticeGenerationRequestDto(
                "request", PracticeDomain.VOCABULARY, "MEANING_RELATION", "ko", "ja", 10,
                3, 2, 6, 2, List.of(), List.of(), List.of(),
                List.of(new PracticeReviewTargetDto("first", "第一", 10.0, 2, List.of()),
                        new PracticeReviewTargetDto("second", "第二", 20.0, 1, List.of())),
                2, now.toLocalDate(), List.of()
        );
        var second = PracticePersistenceService.itemRequest(vocabulary, 2, List.of(), "token");
        assertThat(second.reviewTargets()).extracting(PracticeReviewTargetDto::canonicalKey)
                .containsExactly("second", "first");
        assertThat(second.reviewQuestionCount()).isEqualTo(1);
        assertThat(PracticePersistenceService.itemRequest(vocabulary, 3, List.of(), "token").reviewQuestionCount())
                .isZero();
        assertThat(PracticePersistenceService.itemRequest(vocabulary, 3, List.of(), "token").reviewTargets())
                .hasSize(2);
    }

    @Test
    void itemRequestIdRemainsWithinAiLimitForLongUserAndModeIdentity() {
        var original = request();
        var longIdentity = new AiPracticeGenerationRequestDto(
                "practice-9223372036854775807-2026-09-09-VOCABULARY-USAGE_DISTINCTION-"
                        + java.util.UUID.randomUUID(),
                PracticeDomain.VOCABULARY, "USAGE_DISTINCTION", "ko", "ja", 10,
                3, 2, 6, 2, List.of(), List.of(), List.of(), List.of(), 0,
                original.generationDate(), List.of()
        );
        var item = PracticePersistenceService.itemRequest(longIdentity, 10, List.of(), java.util.UUID.randomUUID().toString());
        assertThat(item.requestId()).hasSizeLessThanOrEqualTo(120);
    }

    @Test
    void contextualChoiceProgressiveRequestsKeepTenSlotMixAndTwoReviewLimit() {
        List<PracticeReviewTargetDto> reviews = List.of(
                reviewTarget("review-a", "復習一", 30.0, 3),
                reviewTarget("review-b", "復習二", 40.0, 2)
        );
        var original = new AiPracticeGenerationRequestDto(
                "contextual", PracticeDomain.VOCABULARY, "CONTEXTUAL_CHOICE",
                "ko", "ja", 10, 4, 2, 6, 2,
                List.of(), List.of(), List.of(), reviews, 2,
                now.toLocalDate(), List.of()
        );
        List<AiPracticeGenerationRequestDto> items = java.util.stream.IntStream
                .rangeClosed(1, 10)
                .mapToObj(order -> PracticePersistenceService.itemRequest(
                        original, order, List.of(), "token-" + order
                ))
                .toList();

        assertThat(items).extracting(AiPracticeGenerationRequestDto::mode)
                .containsOnly("CONTEXTUAL_CHOICE");
        assertThat(items.stream().mapToInt(AiPracticeGenerationRequestDto::easierCount).sum())
                .isEqualTo(2);
        assertThat(items.stream().mapToInt(AiPracticeGenerationRequestDto::currentCount).sum())
                .isEqualTo(6);
        assertThat(items.stream().mapToInt(AiPracticeGenerationRequestDto::challengeCount).sum())
                .isEqualTo(2);
        assertThat(items).extracting(AiPracticeGenerationRequestDto::reviewQuestionCount)
                .containsExactly(1, 1, 0, 0, 0, 0, 0, 0, 0, 0);
        assertThat(items.get(0).reviewTargets().getFirst()).isEqualTo(reviews.get(0));
        assertThat(items.get(1).reviewTargets().getFirst()).isEqualTo(reviews.get(1));
    }

    @Test
    void initialVocabularyItemJsonMatchesAiPracticeRequestContract() throws Exception {
        var item = PracticePersistenceService.itemRequest(
                vocabularyRequest(), 1, List.of(), "initial-token"
        );

        assertAiPracticeRequestJson(item, 0, 0);
        assertThat(item.currentCount()).isEqualTo(1);
        assertThat(item.easierCount()).isZero();
        assertThat(item.challengeCount()).isZero();
    }

    @Test
    void contextualChoicePersistedPlanJsonMatchesAiV3Contract() throws Exception {
        AiPracticeGenerationRequestDto request = PracticePersistenceService.itemRequest(
                contextualChoiceRequest(vocabularyPlan()), 4,
                List.of(
                        contextualChoiceItem(1, PracticeDifficulty.CURRENT, 4, "MEANING",
                                "追加の検証期間", "追加の検証期間", "A"),
                        contextualChoiceItem(2, PracticeDifficulty.EASIER, 3, "NUANCE",
                                "顧客サービスへの影響", "顧客サービスへの影響", "B"),
                        contextualChoiceItem(3, PracticeDifficulty.CURRENT, 4, "COLLOCATION",
                                "個別表現3", "個別表現3", "C")
                ),
                "restored-token"
        );

        var payload = objectMapper.readTree(jsonCodec.write(request));
        var plan = payload.path("vocabularyPlan");
        assertThat(plan.path("version").asText())
                .isEqualTo("personalized-daily-vocabulary-plan-v1");
        assertThat(plan.path("items").size()).isEqualTo(10);
        assertThat(plan.path("items").get(0).fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder(
                        "globalOrder", "reviewTarget", "targetExpression", "canonicalKey",
                        "distractors", "skillTag", "difficulty", "complexityBand",
                        "scenarioFamily", "anchorType", "anchorValue"
                );
        assertThat(plan.path("items").get(0).path("reviewTarget").asBoolean()).isTrue();
        assertThat(plan.path("items").get(0).path("scenarioFamily").isNull()).isTrue();
        assertThat(plan.path("items").get(2).path("reviewTarget").asBoolean()).isFalse();
        assertThat(plan.path("items").get(2).path("anchorType").asText())
                .isEqualTo("SELECTED_KEYWORD");
        assertThat(payload.path("previousQuestions").size()).isEqualTo(3);
        assertThat(payload.path("vocabularyPlanOnly").asBoolean()).isFalse();
        assertThat(jsonCodec.read(jsonCodec.write(request), AiPracticeGenerationRequestDto.class))
                .isEqualTo(request);
    }

    @Test
    void legacyReplaySecondVocabularyItemReconstructedFromPersistedQuestionMatchesAiContract()
            throws Exception {
        PracticeSet vocabularySet = vocabularySet();
        vocabularySet.queueGeneration(jsonCodec.write(vocabularyRequest()));
        PracticeGeneratedQuestionDto firstItem = vocabularyItem(
                1, PracticeDifficulty.CURRENT, 4
        );
        PracticeQuestion firstQuestion = PracticeQuestion.create(
                vocabularySet,
                firstItem,
                jsonCodec.write(firstItem.options()),
                jsonCodec.write(firstItem.correctAnswer()),
                jsonCodec.write(firstItem.vocabularyCandidates())
        );
        when(setRepository.findLockedById(12L)).thenReturn(Optional.of(vocabularySet));
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of(firstQuestion));

        var second = legacyReplayClaim(vocabularySet, now).orElseThrow();

        assertThat(second.order()).isEqualTo(2);
        assertAiPracticeRequestJson(second.request(), 1, 1);
        assertThat(second.request().easierCount()).isEqualTo(1);
        assertThat(second.request().currentCount()).isZero();
        assertThat(second.request().challengeCount()).isZero();
        assertThat(second.request().previousQuestions().getFirst()).isEqualTo(firstItem);
    }

    @Test
    void legacyReplayContextualChoiceThirdItemReconstructedFromPersistedReviewQuestionsMatchesAiContract()
            throws Exception {
        AiPracticeGenerationRequestDto original = contextualChoiceRequest();
        PracticeSet vocabularySet = contextualChoiceSet();
        vocabularySet.queueGeneration(jsonCodec.write(original));
        PracticeGeneratedQuestionDto firstItem = contextualChoiceItem(
                1, PracticeDifficulty.CURRENT, 4, "MEANING",
                "追加の検証期間", "追加の検証期間", "A"
        );
        PracticeGeneratedQuestionDto secondItem = contextualChoiceItem(
                2, PracticeDifficulty.EASIER, 3, "NUANCE",
                "顧客サービスへの影響", "顧客サービスへの影響", "B"
        );
        PracticeQuestion firstQuestion = persistedQuestion(vocabularySet, firstItem);
        PracticeQuestion secondQuestion = persistedQuestion(vocabularySet, secondItem);
        when(setRepository.findLockedById(12L)).thenReturn(Optional.of(vocabularySet));
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of(firstQuestion, secondQuestion));

        var third = legacyReplayClaim(vocabularySet, now).orElseThrow();
        String exactJson = jsonCodec.write(third.request());
        var wire = objectMapper.readTree(exactJson);

        assertThat(third.order()).isEqualTo(3);
        assertThat(third.request().mode()).isEqualTo("CONTEXTUAL_CHOICE");
        assertThat(third.request().previousQuestions()).hasSize(2);
        assertThat(third.request().reviewQuestionCount()).isZero();
        assertThat(third.request().currentCount()).isEqualTo(1);
        assertThat(wire.has("questionOffset")).isFalse();
        assertThat(wire.path("previousQuestions").size()).isEqualTo(2);
        assertThat(wire.path("previousQuestions").get(0).path("order").asInt()).isEqualTo(1);
        assertThat(wire.path("previousQuestions").get(1).path("order").asInt()).isEqualTo(2);
        assertThat(wire.path("reviewTargets").get(0).path("preferredSkill").asText())
                .isEqualTo("MEANING");
        assertThat(wire.path("reviewTargets").get(1).path("preferredSkill").asText())
                .isEqualTo("NUANCE");
    }

    @Test
    void allTenProgressiveContextualChoicePayloadsPreservePrefixAndReviewSlots()
            throws Exception {
        AiPracticeGenerationRequestDto original = contextualChoiceRequest(vocabularyPlan());
        List<PracticeGeneratedQuestionDto> previous = new ArrayList<>();
        String[] skills = {
                "MEANING", "NUANCE", "COLLOCATION", "REGISTER", "PRAGMATIC_FIT",
                "MEANING", "COLLOCATION", "NUANCE", "REGISTER", "PRAGMATIC_FIT"
        };
        String[] keys = {"A", "B", "C", "D"};

        for (int order = 1; order <= 10; order++) {
            AiPracticeGenerationRequestDto itemRequest = PracticePersistenceService.itemRequest(
                    original, order, List.copyOf(previous), "contextual-token-" + order
            );
            var wire = objectMapper.readTree(jsonCodec.write(itemRequest));
            assertThat(itemRequest.mode()).isEqualTo("CONTEXTUAL_CHOICE");
            assertThat(itemRequest.vocabularyPlan()).isEqualTo(vocabularyPlan());
            assertThat(itemRequest.previousQuestions()).hasSize(order - 1);
            assertThat(wire.path("previousQuestions").size()).isEqualTo(order - 1);
            assertThat(wire.path("vocabularyPlan").path("items").size()).isEqualTo(10);
            for (int previousIndex = 0; previousIndex < order - 1; previousIndex++) {
                assertThat(wire.path("previousQuestions").get(previousIndex).path("order").asInt())
                        .isEqualTo(previousIndex + 1);
            }
            assertThat(itemRequest.reviewQuestionCount()).isEqualTo(order <= 2 ? 1 : 0);
            PracticeDifficulty difficulty = itemRequest.easierCount() == 1
                    ? PracticeDifficulty.EASIER
                    : itemRequest.currentCount() == 1
                    ? PracticeDifficulty.CURRENT : PracticeDifficulty.CHALLENGE;
            int band = difficulty == PracticeDifficulty.EASIER ? 3
                    : difficulty == PracticeDifficulty.CHALLENGE ? 5 : 4;
            previous.add(contextualChoiceItem(
                    order,
                    difficulty,
                    band,
                    skills[order - 1],
                    vocabularyPlan().items().get(order - 1).targetExpression(),
                    vocabularyPlan().items().get(order - 1).canonicalKey(),
                    keys[(order - 1) % keys.length]
            ));
        }
    }

    @Test
    void allTenProgressiveVocabularyPayloadsRemainAiContractCompatible() throws Exception {
        AiPracticeGenerationRequestDto original = vocabularyRequest();
        List<PracticeGeneratedQuestionDto> previous = new ArrayList<>();

        for (int order = 1; order <= 10; order++) {
            var itemRequest = PracticePersistenceService.itemRequest(
                    original, order, List.copyOf(previous), "token-" + order
            );
            assertAiPracticeRequestJson(itemRequest, order - 1, order - 1);
            PracticeDifficulty difficulty = itemRequest.easierCount() == 1
                    ? PracticeDifficulty.EASIER
                    : itemRequest.currentCount() == 1
                    ? PracticeDifficulty.CURRENT : PracticeDifficulty.CHALLENGE;
            int band = difficulty == PracticeDifficulty.EASIER ? 3
                    : difficulty == PracticeDifficulty.CHALLENGE ? 5 : 4;
            previous.add(vocabularyItem(order, difficulty, band));
        }
    }

    @Test
    void freshRetryChangesOnlyRequestIdForSameAcceptedPrefix() throws Exception {
        List<PracticeGeneratedQuestionDto> previous = List.of(
                vocabularyItem(1, PracticeDifficulty.CURRENT, 4)
        );
        var first = PracticePersistenceService.itemRequest(
                vocabularyRequest(), 2, previous, "attempt-one"
        );
        var retry = PracticePersistenceService.itemRequest(
                vocabularyRequest(), 2, previous, "attempt-two"
        );
        ObjectNode firstJson = (ObjectNode) objectMapper.readTree(jsonCodec.write(first));
        ObjectNode retryJson = (ObjectNode) objectMapper.readTree(jsonCodec.write(retry));

        assertThat(first.requestId()).isNotEqualTo(retry.requestId());
        firstJson.remove("requestId");
        retryJson.remove("requestId");
        assertThat(retryJson).isEqualTo(firstJson);
        assertAiPracticeRequestJson(first, 1, 1);
        assertAiPracticeRequestJson(retry, 1, 1);
    }

    @Test
    void progressiveVocabularyReviewSlotsRemainAiContractCompatible() throws Exception {
        List<PracticeReviewTargetDto> reviewTargets = List.of(
                reviewTarget("target-a", "target A", 81.0, 3),
                reviewTarget("target-b", "target B", 72.0, 2),
                reviewTarget("target-c", "target C", 63.0, 1)
        );
        AiPracticeGenerationRequestDto base = vocabularyRequest();
        AiPracticeGenerationRequestDto original = new AiPracticeGenerationRequestDto(
                base.requestId(), base.domain(), base.mode(), base.originLanguage(),
                base.learningLanguage(), base.questionCount(), base.complexityBand(),
                base.easierCount(), base.currentCount(), base.challengeCount(),
                base.selectedKeywords(), base.weakSignals(), base.recentMistakes(),
                reviewTargets, 3, base.generationDate(), List.of()
        );
        List<PracticeGeneratedQuestionDto> previous = new ArrayList<>();

        for (int order = 1; order <= 4; order++) {
            AiPracticeGenerationRequestDto itemRequest = PracticePersistenceService.itemRequest(
                    original, order, List.copyOf(previous), "review-token-" + order
            );
            int expectedReviewQuestionCount = order <= 3 ? 1 : 0;
            assertAiPracticeRequestJson(
                    itemRequest, order - 1, order - 1, expectedReviewQuestionCount
            );
            assertThat(itemRequest.questionCount()).isEqualTo(1);
            assertThat(itemRequest.reviewTargets()).hasSize(3);
            assertThat(itemRequest.reviewTargets().getFirst())
                    .isEqualTo(order <= 3 ? reviewTargets.get(order - 1) : reviewTargets.getFirst());
            assertThat(itemRequest.reviewTargets())
                    .allSatisfy(target -> {
                        assertThat(target.canonicalKey()).isNotBlank();
                        assertThat(target.expression()).isNotBlank();
                        assertThat(target.wrongCount()).isGreaterThanOrEqualTo(0);
                        assertThat(target.previousQuestionTypes()).isNotNull();
                    });
            var wirePayload = objectMapper.readTree(jsonCodec.write(itemRequest));
            for (int targetIndex = 0; targetIndex < itemRequest.reviewTargets().size(); targetIndex++) {
                PracticeReviewTargetDto expected = itemRequest.reviewTargets().get(targetIndex);
                var serialized = wirePayload.path("reviewTargets").get(targetIndex);
                assertThat(serialized.size()).isEqualTo(5);
                assertThat(serialized.path("canonicalKey").asText()).isEqualTo(expected.canonicalKey());
                assertThat(serialized.path("expression").asText()).isEqualTo(expected.expression());
                assertThat(serialized.path("masteryScore").asDouble()).isEqualTo(expected.masteryScore());
                assertThat(serialized.path("wrongCount").asInt()).isEqualTo(expected.wrongCount());
                assertThat(serialized.path("previousQuestionTypes").get(0).asText())
                        .isEqualTo("SINGLE_CHOICE");
            }
            assertThat(itemRequest.previousQuestions())
                    .allSatisfy(question -> assertThat(question.reviewTarget()).isTrue());

            if (order <= 3) {
                PracticeDifficulty difficulty = itemRequest.easierCount() == 1
                        ? PracticeDifficulty.EASIER : PracticeDifficulty.CURRENT;
                int band = difficulty == PracticeDifficulty.EASIER ? 3 : 4;
                previous.add(vocabularyItem(order, difficulty, band, true));
            }
        }

        List<PracticeGeneratedQuestionDto> acceptedPrefix = List.of(previous.getFirst());
        AiPracticeGenerationRequestDto firstAttempt = PracticePersistenceService.itemRequest(
                original, 2, acceptedPrefix, "review-retry-one"
        );
        AiPracticeGenerationRequestDto retry = PracticePersistenceService.itemRequest(
                original, 2, acceptedPrefix, "review-retry-two"
        );
        ObjectNode firstJson = (ObjectNode) objectMapper.readTree(jsonCodec.write(firstAttempt));
        ObjectNode retryJson = (ObjectNode) objectMapper.readTree(jsonCodec.write(retry));
        firstJson.remove("requestId");
        retryJson.remove("requestId");

        assertThat(firstAttempt.requestId()).isNotEqualTo(retry.requestId());
        assertThat(retryJson).isEqualTo(firstJson);
        assertThat(retry.reviewQuestionCount()).isEqualTo(1);
        assertThat(retry.reviewTargets().getFirst()).isEqualTo(reviewTargets.get(1));
        assertAiPracticeRequestJson(retry, 1, 1, 1);
    }

    @Test
    void existingFullyGeneratedSetDefaultsToReady() {
        assertThat(set.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.READY);
    }

    @Test
    void allPresentClaimDoesNotRegenerateQuestions() {
        set.queueGeneration(jsonCodec.write(request()));
        lockSet();
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of(question(1), question(2), question(3), question(4), question(5)));

        assertThat(service.claim(12L, now, now.minusMinutes(30), 3)).isEmpty();
        assertThat(set.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.READY);
        verify(questionRepository, never()).save(any());
    }

    /** Offline legacy contract replay, deliberately not the retired public claim entry. */
    private Optional<PracticePersistenceService.GenerationClaim> legacyReplayClaim(
            PracticeSet legacySet, LocalDateTime at
    ) {
        assertThat(legacySet.getDomain()).isEqualTo(PracticeDomain.VOCABULARY);
        assertThat(setRepository.findLockedById(legacySet.getId()).orElseThrow()).isSameAs(legacySet);
        var questions = questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(legacySet.getId());
        boolean stale = legacySet.getGenerationStatus() == PracticeGenerationStatus.GENERATING
                && legacySet.getGenerationStartedAt().isBefore(at.minusMinutes(30));
        return ReflectionTestUtils.invokeMethod(service, "claimAvailable", legacySet, questions, at, stale, 3);
    }

    private void lockSet() {
        when(setRepository.findLockedById(12L)).thenReturn(Optional.of(set));
    }

    private PracticeQuestion question(int order) {
        return PracticeQuestion.create(set, item(order), "[{\"key\":\"A\",\"text\":\"はい\"}]", "[\"A\"]", "[]");
    }

    private PracticeSet vocabularySet() {
        PracticeSet value = PracticeSet.create(
                user,
                now.toLocalDate(),
                PracticeDomain.VOCABULARY,
                "MEANING_RELATION",
                "ko",
                "ja",
                10,
                4
        );
        ReflectionTestUtils.setField(value, "id", 12L);
        return value;
    }

    private PracticeSet contextualChoiceSet() {
        PracticeSet value = PracticeSet.create(
                user,
                now.toLocalDate(),
                PracticeDomain.VOCABULARY,
                "CONTEXTUAL_CHOICE",
                "ko",
                "ja",
                10,
                4
        );
        ReflectionTestUtils.setField(value, "id", 12L);
        return value;
    }

    private AiPracticeGenerationRequestDto contextualChoiceRequest() {
        return contextualChoiceRequest(null);
    }

    private AiPracticeGenerationRequestDto contextualChoiceRequest(
            PersonalizedVocabularyPlanDto vocabularyPlan
    ) {
        return new AiPracticeGenerationRequestDto(
                "practice-contextual-choice",
                PracticeDomain.VOCABULARY,
                "CONTEXTUAL_CHOICE",
                "ko",
                "ja",
                10,
                4,
                2,
                6,
                2,
                List.of("業務", "協業"),
                List.of(),
                List.of(),
                List.of(
                        reviewTarget("追加の検証期間", "追加の検証期間", 70.0, 2, "MEANING"),
                        reviewTarget(
                                "顧客サービスへの影響", "顧客サービスへの影響", 65.0, 1, "NUANCE"
                        )
                ),
                2,
                now.toLocalDate(),
                List.of(),
                vocabularyPlan
        );
    }

    private AiPracticeGenerationResponseDto contextualChoiceResponse(
            String requestId,
            PersonalizedVocabularyPlanDto vocabularyPlan
    ) {
        return new AiPracticeGenerationResponseDto(
                requestId,
                "vocabulary-contextual-choice-recipe-v3",
                PracticeDomain.VOCABULARY,
                "CONTEXTUAL_CHOICE",
                4,
                List.of(contextualChoiceItem(
                        vocabularyPlan,
                        1,
                        PracticeDifficulty.CURRENT,
                        4,
                        "MEANING",
                        "追加の検証期間",
                        "追加の検証期間",
                        "A"
                )),
                vocabularyPlan
        );
    }

    static PersonalizedVocabularyPlanDto vocabularyPlan() {
        String[] skills = {
                "MEANING", "NUANCE", "COLLOCATION", "REGISTER", "PRAGMATIC_FIT",
                "MEANING", "COLLOCATION", "NUANCE", "REGISTER", "PRAGMATIC_FIT"
        };
        PracticeDifficulty[] difficulties = {
                PracticeDifficulty.CURRENT, PracticeDifficulty.EASIER,
                PracticeDifficulty.CURRENT, PracticeDifficulty.CHALLENGE,
                PracticeDifficulty.CURRENT, PracticeDifficulty.EASIER,
                PracticeDifficulty.CURRENT, PracticeDifficulty.CHALLENGE,
                PracticeDifficulty.CURRENT, PracticeDifficulty.CURRENT
        };
        List<VocabularyPlanItemDto> items = new ArrayList<>();
        for (int order = 1; order <= 10; order++) {
            boolean review = order <= 2;
            String target = order == 1 ? "追加の検証期間"
                    : order == 2 ? "顧客サービスへの影響" : "個別表現" + order;
            int band = difficulties[order - 1] == PracticeDifficulty.EASIER ? 3
                    : difficulties[order - 1] == PracticeDifficulty.CHALLENGE ? 5 : 4;
            items.add(new VocabularyPlanItemDto(
                    order,
                    review,
                    target,
                    target,
                    List.of("関連候補" + order + "一", "関連候補" + order + "二", "関連候補" + order + "三"),
                    skills[order - 1],
                    difficulties[order - 1],
                    band,
                    review ? null : "SCENARIO_" + order,
                    review ? null : "SELECTED_KEYWORD",
                    review ? null : "業務"
            ));
        }
        return new PersonalizedVocabularyPlanDto(
                "personalized-daily-vocabulary-plan-v1", List.copyOf(items)
        );
    }

    private static PersonalizedVocabularyPlanDto withPlanItem(
            PersonalizedVocabularyPlanDto plan, int order, VocabularyPlanItemDto item
    ) {
        var revised = new ArrayList<>(plan.items());
        revised.set(order - 1, item);
        return new PersonalizedVocabularyPlanDto(plan.version(), List.copyOf(revised));
    }

    private PracticeGeneratedQuestionDto contextualChoiceItem(
            int order,
            PracticeDifficulty difficulty,
            int complexityBand,
            String skillTag,
            String targetExpression,
            String canonicalKey,
            String correctKey
    ) {
        return contextualChoiceItem(vocabularyPlan(), order, difficulty, complexityBand,
                skillTag, targetExpression, canonicalKey, correctKey);
    }

    private PracticeGeneratedQuestionDto contextualChoiceItem(
            PersonalizedVocabularyPlanDto plan,
            int order,
            PracticeDifficulty difficulty,
            int complexityBand,
            String skillTag,
            String targetExpression,
            String canonicalKey,
            String correctKey
    ) {
        var optionTexts = new ArrayList<>(plan.items().get(order - 1).distractors());
        optionTexts.add((order - 1) % 4, targetExpression);
        return new PracticeGeneratedQuestionDto(
                order,
                PracticeQuestionType.SINGLE_CHOICE,
                difficulty,
                complexityBand,
                null,
                null,
                "状況を確認した結果、担当者は______ことにしました。",
                List.of(
                        new PracticeOptionDto("A", optionTexts.get(0)),
                        new PracticeOptionDto("B", optionTexts.get(1)),
                        new PracticeOptionDto("C", optionTexts.get(2)),
                        new PracticeOptionDto("D", optionTexts.get(3))
                ),
                List.of(correctKey),
                skillTag,
                null,
                "문맥상 이 표현이 가장 자연스럽습니다.",
                "文脈上、この表現が最も自然です。",
                targetExpression,
                canonicalKey,
                order <= 2,
                List.of()
        );
    }

    private PracticeQuestion persistedQuestion(
            PracticeSet practiceSet,
            PracticeGeneratedQuestionDto item
    ) {
        return PracticeQuestion.create(
                practiceSet,
                item,
                jsonCodec.write(item.options()),
                jsonCodec.write(item.correctAnswer()),
                jsonCodec.write(item.vocabularyCandidates())
        );
    }

    private AiPracticeGenerationRequestDto vocabularyRequest() {
        return new AiPracticeGenerationRequestDto(
                "practice-vocabulary",
                PracticeDomain.VOCABULARY,
                "MEANING_RELATION",
                "ko",
                "ja",
                10,
                4,
                2,
                6,
                2,
                List.of("導入"),
                List.of("DISTINCTION"),
                List.of("DISTINCTION:安全な導入"),
                List.of(),
                0,
                now.toLocalDate(),
                List.of()
        );
    }

    private PracticeGeneratedQuestionDto vocabularyItem(
            int order,
            PracticeDifficulty difficulty,
            int complexityBand
    ) {
        return vocabularyItem(order, difficulty, complexityBand, false);
    }

    private PracticeGeneratedQuestionDto vocabularyItem(
            int order,
            PracticeDifficulty difficulty,
            int complexityBand,
            boolean reviewTarget
    ) {
        return new PracticeGeneratedQuestionDto(
                order,
                PracticeQuestionType.SINGLE_CHOICE,
                difficulty,
                complexityBand,
                null,
                null,
                "文脈に最も適切な表現を選んでください。",
                List.of(
                        new PracticeOptionDto("A", "安全な展開"),
                        new PracticeOptionDto("B", "安全な公開"),
                        new PracticeOptionDto("C", "安全な運用"),
                        new PracticeOptionDto("D", "安全な移行")
                ),
                List.of("A"),
                "DISTINCTION",
                null,
                "문맥상 범위와 뉘앙스가 가장 정확합니다.",
                "文脈上の範囲とニュアンスが最も正確です。",
                "安全な導入-" + order,
                "safe-deployment-" + order,
                reviewTarget,
                List.of()
        );
    }

    private PracticeReviewTargetDto reviewTarget(
            String canonicalKey,
            String expression,
            double masteryScore,
            int wrongCount
    ) {
        return new PracticeReviewTargetDto(
                canonicalKey,
                expression,
                masteryScore,
                wrongCount,
                List.of(PracticeQuestionType.SINGLE_CHOICE)
        );
    }

    private PracticeReviewTargetDto reviewTarget(
            String canonicalKey,
            String expression,
            double masteryScore,
            int wrongCount,
            String preferredSkill
    ) {
        return new PracticeReviewTargetDto(
                canonicalKey,
                expression,
                masteryScore,
                wrongCount,
                List.of(PracticeQuestionType.SINGLE_CHOICE),
                preferredSkill
        );
    }

    private void assertAiPracticeRequestJson(
            AiPracticeGenerationRequestDto request,
            int previousQuestionCount,
            int expectedQuestionOffset
    ) throws Exception {
        assertAiPracticeRequestJson(request, previousQuestionCount, expectedQuestionOffset, 0);
    }

    private void assertAiPracticeRequestJson(
            AiPracticeGenerationRequestDto request,
            int previousQuestionCount,
            int expectedQuestionOffset,
            int expectedReviewQuestionCount
    ) throws Exception {
        var payload = objectMapper.readTree(jsonCodec.write(request));
        assertThat(payload.size()).isEqualTo(19);
        assertThat(payload.path("requestId").asText()).isEqualTo(request.requestId());
        assertThat(payload.path("domain").asText()).isEqualTo("VOCABULARY");
        assertThat(payload.path("mode").asText()).isEqualTo("MEANING_RELATION");
        assertThat(payload.path("originLanguage").asText()).isEqualTo("ko");
        assertThat(payload.path("learningLanguage").asText()).isEqualTo("ja");
        assertThat(payload.path("questionCount").asInt()).isEqualTo(1);
        assertThat(payload.path("complexityBand").asInt()).isEqualTo(4);
        assertThat(payload.path("easierCount").asInt()
                + payload.path("currentCount").asInt()
                + payload.path("challengeCount").asInt()).isEqualTo(1);
        assertThat(payload.path("selectedKeywords").isArray()).isTrue();
        assertThat(payload.path("weakSignals").isArray()).isTrue();
        assertThat(payload.path("recentMistakes").isArray()).isTrue();
        assertThat(payload.path("reviewTargets").isArray()).isTrue();
        assertThat(payload.path("reviewQuestionCount").asInt())
                .isEqualTo(expectedReviewQuestionCount);
        assertThat(payload.path("generationDate").asText()).isEqualTo(now.toLocalDate().toString());
        assertThat(payload.path("previousQuestions").size()).isEqualTo(previousQuestionCount);
        assertThat(payload.path("vocabularyPlan").isNull()).isTrue();
        assertThat(payload.path("vocabularyPlanOnly").asBoolean()).isFalse();
        assertThat(payload.has("questionOffset")).isFalse();
        assertThat(request.previousQuestions().size()).isEqualTo(expectedQuestionOffset);
        for (int index = 0; index < previousQuestionCount; index++) {
            var question = payload.path("previousQuestions").get(index);
            assertThat(question.path("order").asInt()).isEqualTo(index + 1);
            assertThat(question.path("questionType").asText()).isEqualTo("SINGLE_CHOICE");
            assertThat(question.path("difficulty").asText()).isNotBlank();
            assertThat(question.path("complexityBand").asInt()).isBetween(1, 5);
            assertThat(question.path("passageId").isNull()).isTrue();
            assertThat(question.path("passageText").isNull()).isTrue();
            assertThat(question.path("prompt").asText()).isNotBlank();
            assertThat(question.path("options").size()).isEqualTo(4);
            assertThat(question.path("correctAnswer").size()).isEqualTo(1);
            assertThat(question.path("skillTag").asText()).isEqualTo("DISTINCTION");
            assertThat(question.path("evidenceText").isNull()).isTrue();
            assertThat(question.path("explanationLearning").asText()).isNotBlank();
            assertThat(question.path("explanationOrigin").asText()).isNotBlank();
            assertThat(question.path("targetExpression").asText()).isNotBlank();
            assertThat(question.path("canonicalKey").asText()).isNotBlank();
            assertThat(question.path("reviewTarget").asBoolean())
                    .isEqualTo(request.previousQuestions().get(index).reviewTarget());
            assertThat(question.path("vocabularyCandidates").isArray()).isTrue();
        }
    }

    private PracticePersistenceService.GenerationClaim claim(int order, String token) {
        return new PracticePersistenceService.GenerationClaim(12L, order, token, 0, request());
    }

    static AiPracticeGenerationRequestDto request() {
        return new AiPracticeGenerationRequestDto("request", PracticeDomain.READING, "COMPREHENSION",
                "ko", "ja", 5, 3, 1, 3, 1, List.of(), List.of(), List.of(), List.of(), 0,
                LocalDate.of(2026, 9, 9), List.of());
    }

    static PracticeGeneratedQuestionDto item(int order) {
        return new PracticeGeneratedQuestionDto(order, PracticeQuestionType.SINGLE_CHOICE,
                PracticeDifficulty.EASIER, 3, "p1", "本文です。", "問題です。",
                List.of(new PracticeOptionDto("A", "はい")), List.of("A"), "MAIN_IDEA", "本文", "설명", "説明",
                null, null, false, List.of());
    }

    static AiPracticeGenerationResponseDto response() {
        return new AiPracticeGenerationResponseDto("request", "practice", PracticeDomain.READING,
                "COMPREHENSION", 3, List.of(item(1)));
    }
}
