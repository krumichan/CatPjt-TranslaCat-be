package jp.co.translacat.domain.accountbook.analysis.service;

import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.PeriodType;
import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.Request;
import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.Response;
import jp.co.translacat.domain.accountbook.analysis.repository.SpendingAnalysisJobStore;
import jp.co.translacat.domain.accountbook.analysis.repository.SpendingAnalysisJobStore.Job;
import jp.co.translacat.domain.accountbook.analysis.repository.SpendingAnalysisJobStore.Status;
import jp.co.translacat.domain.accountbook.member.enums.AccountBookMemberRole;
import jp.co.translacat.domain.accountbook.member.repository.AccountBookMemberRepository;
import jp.co.translacat.domain.support.service.SupportNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpendingAnalysisJobServiceTest {
    private final SpendingAnalysisJobStore store = mock(SpendingAnalysisJobStore.class);
    private final AccountBookAccessService access = mock(AccountBookAccessService.class);
    private final AccountBookMemberRepository members = mock(AccountBookMemberRepository.class);
    private final SpendingAnalysisService analysis = mock(SpendingAnalysisService.class);
    private final SpendingAnalysisHistoryService history = mock(SpendingAnalysisHistoryService.class);
    private final SupportNotificationService notifications = mock(SupportNotificationService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final SpendingAnalysisJobService jobs =
            new SpendingAnalysisJobService(store, access, members, analysis, history, notifications, transactions);
    private final Request request = new Request(PeriodType.YEARLY, 2026, null, "ko", null);
    private final LocalDate start = LocalDate.of(2026, 1, 1);
    private final LocalDate end = start.plusYears(1);
    private final Job queued = new Job(13, 1, 7, start, end, Status.QUEUED, null, null);

    @BeforeEach
    void setupTransaction() {
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
    }

    @Test
    void onlyOwnerCanCancelEvenIfMemberRequestedAnalysis() {
        // 준비: 요청자는 7번, 가계부 소유자는 8번이다.
        when(store.active(1)).thenReturn(Optional.of(queued));
        when(members.existsByAccountBook_IdAndUser_IdAndRoleAndDeletedFalse(1L, 8L, AccountBookMemberRole.OWNER))
                .thenReturn(true);

        // 실행 / 검증
        assertThat(jobs.current(1, 7).canCancel()).isFalse();
        assertThat(jobs.current(1, 8).canCancel()).isTrue();
        assertThatThrownBy(() -> jobs.cancel(1, 7, 13)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(store, never()).cancel(anyLong());

        // 검증: 소유자는 타인이 요청한 작업도 취소할 수 있다.
        var canceled = new Job(13, 1, 7, start, end, Status.CANCELED, null, null);
        when(store.lock(1, 13)).thenReturn(Optional.of(queued), Optional.of(canceled));
        assertThat(jobs.cancel(1, 8, 13).status()).isEqualTo(Status.CANCELED);
        verify(store).cancel(13);
    }

    @Test
    void duplicateIsRejectedForBothLegacyAndBackgroundEntryPoints() {
        // 준비: 다른 사용자의 작업이 DB의 가계부 슬롯을 점유하고 있다.
        when(store.insert(eq(1L), eq(7L), eq(request), any(), nullable(Instant.class)))
                .thenThrow(new DuplicateKeyException("active job"));

        // 실행 / 검증
        assertThatThrownBy(() -> jobs.enqueue(1, 7, request)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> jobs.runLegacy(1, 7, request)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getReason()).isEqualTo("ANALYSIS_IN_PROGRESS"));
        verifyNoInteractions(analysis, history, notifications);
    }

    @Test
    void completionSavesAndNotifiesRequesterOnceButLateCanceledOutputIsDiscarded() {
        // 준비: 워커가 작업을 가져와 응답을 받았다.
        when(store.nextQueued()).thenReturn(Optional.of(queued));
        when(store.claim(eq(13L), any())).thenReturn(true);
        when(store.request(13)).thenReturn(request);
        var result = mock(Response.class);
        when(result.analysisId()).thenReturn("result-id");
        when(analysis.generate(eq(1L), eq(7L), eq(request), eq(false), any())).thenReturn(result);
        when(store.complete(eq(13L), eq("result-id"), any())).thenReturn(false, true);

        // 실행 / 검증: 취소가 먼저 반영되면 저장이나 알림이 없다.
        jobs.runNext();
        verifyNoInteractions(history, notifications);
        verify(store).finishWithoutResult(13, "SPENDING_ANALYSIS_FAILED");

        // 실행 / 검증: 완료가 허용된 작업은 요청자에게만 알림을 남긴다.
        jobs.runNext();
        verify(history).save(1L, 7L, result);
        verify(notifications).notifySpendingAnalysisCompleted(7L, 1L, 13L, start, end);
        verifyNoMoreInteractions(notifications);
    }
}
