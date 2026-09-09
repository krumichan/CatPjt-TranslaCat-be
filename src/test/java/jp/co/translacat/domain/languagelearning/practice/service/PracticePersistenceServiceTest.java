package jp.co.translacat.domain.languagelearning.practice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final LanguageLearningJsonCodec jsonCodec = new LanguageLearningJsonCodec(
            new ObjectMapper().findAndRegisterModules()
    );
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

        var claim = service.claim(12L, now, now.minusMinutes(30)).orElseThrow();

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

        assertThat(service.claim(12L, now, now.minusMinutes(30))).isEmpty();
        verifyNoInteractions(questionRepository);
    }

    @Test
    void expiredClaimIsFencedByDifferentToken() {
        set.queueGeneration(jsonCodec.write(request()));
        set.claimGeneration("expired", now.minusHours(1));
        lockSet();

        var claim = service.claim(12L, now, now.minusMinutes(30)).orElseThrow();

        assertThat(claim.token()).isNotEqualTo("expired");
        assertThat(set.ownsGeneration("expired")).isFalse();
        assertThat(set.ownsGeneration(claim.token())).isTrue();
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
    void existingFullyGeneratedSetDefaultsToReady() {
        assertThat(set.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.READY);
    }

    @Test
    void allPresentClaimDoesNotRegenerateQuestions() {
        set.queueGeneration(jsonCodec.write(request()));
        lockSet();
        when(questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(12L))
                .thenReturn(List.of(question(1), question(2), question(3), question(4), question(5)));

        assertThat(service.claim(12L, now, now.minusMinutes(30))).isEmpty();
        assertThat(set.getGenerationStatus()).isEqualTo(PracticeGenerationStatus.READY);
        verify(questionRepository, never()).save(any());
    }

    private void lockSet() {
        when(setRepository.findLockedById(12L)).thenReturn(Optional.of(set));
    }

    private PracticeQuestion question(int order) {
        return PracticeQuestion.create(set, item(order), "[{\"key\":\"A\",\"text\":\"はい\"}]", "[\"A\"]", "[]");
    }

    private PracticePersistenceService.GenerationClaim claim(int order, String token) {
        return new PracticePersistenceService.GenerationClaim(12L, order, token, request());
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
