package jp.co.translacat.domain.languagelearning.practice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeGeneratedQuestionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeOptionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeReviewTargetDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiPracticeGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiPracticeGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeQuestionType;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeSetStatus;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeQuestion;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeSet;
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
    void successfulItemQueuesAndClaimsNextMissingOrderWithPersistedPrefix() {
        set.queueGeneration(jsonCodec.write(request()));
        lockSet();
        PracticeQuestion firstQuestion = question(1);
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of(), List.of(), List.of(firstQuestion));

        var first = service.claim(12L, now, now.minusMinutes(30), 3).orElseThrow();
        service.append(first, response());
        var second = service.claim(12L, now.plusSeconds(1), now.minusMinutes(30), 3).orElseThrow();

        assertThat(set.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.GENERATING);
        assertThat(second.order()).isEqualTo(2);
        assertThat(second.token()).isNotEqualTo(first.token());
        assertThat(second.request().requestId()).isNotEqualTo(first.request().requestId());
        assertThat(second.request().previousQuestions())
                .extracting(PracticeGeneratedQuestionDto::order)
                .containsExactly(1);
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
    void oneItemRequestsKeepGlobalDifficultyMixAndReviewSelection() {
        var base = request();
        List<AiPracticeGenerationRequestDto> items = java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(order -> PracticePersistenceService.itemRequest(base, order, List.of(), "token"))
                .toList();
        assertThat(items.stream().mapToInt(AiPracticeGenerationRequestDto::easierCount).sum()).isEqualTo(1);
        assertThat(items.stream().mapToInt(AiPracticeGenerationRequestDto::currentCount).sum()).isEqualTo(3);
        assertThat(items.stream().mapToInt(AiPracticeGenerationRequestDto::challengeCount).sum()).isEqualTo(1);
        assertThat(items).extracting(AiPracticeGenerationRequestDto::currentCount)
                .containsExactly(1, 0, 1, 0, 1);
        assertThat(items).extracting(AiPracticeGenerationRequestDto::challengeCount)
                .containsExactly(0, 0, 0, 1, 0);
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
    void secondVocabularyItemReconstructedFromPersistedQuestionMatchesAiContract()
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

        var second = service.claim(12L, now, now.minusMinutes(30), 3).orElseThrow();

        assertThat(second.order()).isEqualTo(2);
        assertAiPracticeRequestJson(second.request(), 1, 1);
        assertThat(second.request().easierCount()).isEqualTo(1);
        assertThat(second.request().currentCount()).isZero();
        assertThat(second.request().challengeCount()).isZero();
        assertThat(second.request().previousQuestions().getFirst()).isEqualTo(firstItem);
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
        assertThat(payload.size()).isEqualTo(17);
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
