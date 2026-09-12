package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiDailyWritingGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiDailyWritingGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.common.enums.DailySetStatus;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingItem;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.factory.DailyWritingGenerationRequestFactory;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingSnapshot;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingItemRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingAnswerRepository;
import jp.co.translacat.domain.languagelearning.daily.service.DailyWritingRegenerationStateCommandService.RegenerationClaim;
import jp.co.translacat.domain.languagelearning.daily.service.DailyWritingRegenerationStateCommandService.RegenerationTarget;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityContext;
import jp.co.translacat.domain.user.entity.User;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyWritingRegenerationStateCommandServiceTest {

    @Mock private DailyWritingSetRepository dailySetRepository;
    @Mock private DailyWritingItemRepository itemRepository;
    @Mock private WritingAnswerRepository answerRepository;
    @Mock private DailyWritingSnapshotService snapshotService;
    @Mock private DailyWritingGenerationRequestFactory requestFactory;
    @Mock private DailyWritingItemCommandService itemCommandService;
    @Mock private DailyWritingItemRevisionService itemRevisionService;
    @Mock private DailyWritingSet dailySet;
    @Mock private DailyWritingItem item;
    @Mock private User user;
    @Mock private DailyWritingSnapshot snapshot;

    private DailyWritingRegenerationStateCommandService service;

    @BeforeEach
    void setUp() {
        service = new DailyWritingRegenerationStateCommandService(
                dailySetRepository,
                itemRepository,
                answerRepository,
                snapshotService,
                requestFactory,
                itemCommandService,
                itemRevisionService
        );
    }

    @Test
    void claimMarksSetBeforeReturningAiRequest() {
        stubClaimableSet();
        when(itemRepository.findAllByDailySetIdOrderByOrderNoAsc(20L))
                .thenReturn(List.of(item));
        when(answerRepository.existsByDailyItemId(30L))
                .thenReturn(false);
        when(snapshotService.read(dailySet)).thenReturn(snapshot);
        when(snapshot.learningLanguage()).thenReturn("ja");
        when(itemRevisionService.revision(item)).thenReturn("rev-1");
        AiDailyWritingGenerationRequestDto request = request("request-id");
        when(requestFactory.createRegeneration(
                eq(10L),
                eq(dailySet),
                eq(snapshot),
                eq(1),
                any(DifficultyDistributionDto.class),
                anyString()
        )).thenReturn(request);

        RegenerationClaim claim = service.claim(10L, 20L);

        assertThat(claim.dailySetId()).isEqualTo(20L);
        assertThat(claim.expectedCount()).isEqualTo(1);
        assertThat(claim.request()).isSameAs(request);
        assertThat(claim.targets()).hasSize(1);
        RegenerationTarget target = claim.targets().get(0);
        assertThat(target.itemId()).isEqualTo(30L);
        assertThat(target.contentRevision()).isEqualTo("rev-1");
        ArgumentCaptor<String> tokenCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(dailySet).claimRegeneration(
                tokenCaptor.capture(),
                any()
        );
        assertThat(tokenCaptor.getValue()).isEqualTo(claim.token());
    }

    @Test
    void activeRegenerationCannotBeClaimedAgain() {
        stubOwnedSet();
        when(dailySet.getStatus()).thenReturn(DailySetStatus.READY);
        when(dailySet.getRegenerationCount()).thenReturn(0);
        when(dailySet.canClaimRegeneration(any())).thenReturn(false);

        assertThatThrownBy(() -> service.claim(10L, 20L))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(
                        ((BusinessException) throwable).getErrorCode()
                ).isEqualTo(
                        "LANGUAGE_LEARNING_WRITING_REGENERATION_IN_PROGRESS"
                ));

        verify(snapshotService, never()).read(any());
    }

    @Test
    void publishRejectsAnswerThatAppearedDuringAiCall() {
        RegenerationClaim claim = publishableClaim("rev-1");
        AiDailyWritingGenerationResponseDto response = response(
                claim.request().requestId()
        );
        stubPublishableSet(claim);
        when(itemRepository.findAllByDailySetIdOrderByOrderNoAsc(20L))
                .thenReturn(List.of(item));
        when(itemRevisionService.revision(item)).thenReturn("rev-1");
        when(answerRepository.existsByDailyItemId(30L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.publish(claim, response))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(
                        ((BusinessException) throwable).getErrorCode()
                ).isEqualTo(
                        "LANGUAGE_LEARNING_WRITING_REGENERATION_CONFLICT"
                ));

        verify(itemCommandService, never()).replaceAll(
                anyString(),
                any(),
                any()
        );
    }

    @Test
    void publishRejectsContentChangedDuringAiCall() {
        RegenerationClaim claim = publishableClaim("rev-1");
        AiDailyWritingGenerationResponseDto response = response(
                claim.request().requestId()
        );
        stubPublishableSet(claim);
        when(itemRepository.findAllByDailySetIdOrderByOrderNoAsc(20L))
                .thenReturn(List.of(item));
        when(itemRevisionService.revision(item)).thenReturn("rev-2");

        assertThatThrownBy(() -> service.publish(claim, response))
                .isInstanceOf(BusinessException.class);

        verify(itemCommandService, never()).replaceAll(
                anyString(),
                any(),
                any()
        );
    }

    @Test
    void publishReplacesOnlyStillUnansweredUnchangedTargets() {
        RegenerationClaim claim = publishableClaim("rev-1");
        AiDailyWritingGenerationResponseDto response = response(
                claim.request().requestId()
        );
        stubPublishableSet(claim);
        when(itemRepository.findAllByDailySetIdOrderByOrderNoAsc(20L))
                .thenReturn(List.of(item));
        when(itemRevisionService.revision(item)).thenReturn("rev-1");
        when(answerRepository.existsByDailyItemId(30L))
                .thenReturn(false);

        DailyWritingSet result = service.publish(claim, response);

        assertThat(result).isSameAs(dailySet);
        verify(itemCommandService).replaceAll(
                "ja",
                List.of(item),
                response.items()
        );
        verify(dailySet).incrementRegeneration();
        verify(dailySet).releaseRegeneration("token");
    }

    private void stubClaimableSet() {
        stubOwnedSet();
        when(dailySet.getId()).thenReturn(20L);
        when(dailySet.getStatus()).thenReturn(DailySetStatus.READY);
        when(dailySet.getRegenerationCount()).thenReturn(0);
        when(dailySet.canClaimRegeneration(any())).thenReturn(true);
        when(item.getId()).thenReturn(30L);
        when(item.getOrderNo()).thenReturn(2);
        when(item.getDifficulty()).thenReturn(DailyWritingDifficulty.NORMAL);
    }

    private void stubOwnedSet() {
        when(dailySetRepository.findLockedById(20L))
                .thenReturn(Optional.of(dailySet));
        when(dailySet.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(10L);
    }

    private void stubPublishableSet(RegenerationClaim claim) {
        when(dailySetRepository.findLockedById(20L))
                .thenReturn(Optional.of(dailySet));
        when(dailySet.ownsRegeneration(claim.token()))
                .thenReturn(true);
        when(item.getId()).thenReturn(30L);
        when(item.getOrderNo()).thenReturn(2);
        when(item.getDifficulty()).thenReturn(DailyWritingDifficulty.NORMAL);
    }

    private RegenerationClaim publishableClaim(String revision) {
        DifficultyDistributionDto distribution =
                new DifficultyDistributionDto(0, 1, 0);
        return new RegenerationClaim(
                20L,
                "token",
                request("request-id"),
                List.of(new RegenerationTarget(
                        30L,
                        2,
                        DailyWritingDifficulty.NORMAL,
                        revision
                )),
                distribution,
                DailyWritingType.TRANSLATION,
                "ja"
        );
    }

    private AiDailyWritingGenerationRequestDto request(String requestId) {
        return new AiDailyWritingGenerationRequestDto(
                requestId,
                "ko",
                "ja",
                DailyWritingType.TRANSLATION,
                1,
                new DifficultyDistributionDto(0, 1, 0),
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                LocalDate.of(2026, 9, 12),
                "snapshot",
                null,
                DiversityContext.empty(),
                null
        );
    }

    private AiDailyWritingGenerationResponseDto response(
            String requestId
    ) {
        return new AiDailyWritingGenerationResponseDto(
                requestId,
                "prompt",
                List.of()
        );
    }
}
