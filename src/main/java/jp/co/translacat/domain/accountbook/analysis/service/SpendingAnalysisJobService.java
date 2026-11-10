package jp.co.translacat.domain.accountbook.analysis.service;

import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.Request;
import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.Response;
import jp.co.translacat.domain.accountbook.analysis.repository.SpendingAnalysisJobStore;
import jp.co.translacat.domain.accountbook.analysis.repository.SpendingAnalysisJobStore.Job;
import jp.co.translacat.domain.accountbook.analysis.repository.SpendingAnalysisJobStore.Status;
import jp.co.translacat.domain.accountbook.member.enums.AccountBookMemberRole;
import jp.co.translacat.domain.accountbook.member.repository.AccountBookMemberRepository;
import jp.co.translacat.domain.support.service.SupportNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.CancellationException;

@Service
@Slf4j
public class SpendingAnalysisJobService {
    private static final Duration LEASE = Duration.ofMinutes(10);
    // AiServerClient의 250초 HTTP 제한에 결과 전송 여유를 더한다.
    private static final Duration PROVIDER_BUDGET = Duration.ofSeconds(270);
    private static final String FAILED = "SPENDING_ANALYSIS_FAILED";

    private final SpendingAnalysisJobStore store;
    private final AccountBookAccessService access;
    private final AccountBookMemberRepository members;
    private final SpendingAnalysisService analysis;
    private final SpendingAnalysisHistoryService history;
    private final SupportNotificationService notifications;
    private final TransactionTemplate tx;

    public SpendingAnalysisJobService(SpendingAnalysisJobStore store, AccountBookAccessService access,
                                      AccountBookMemberRepository members, SpendingAnalysisService analysis,
                                      SpendingAnalysisHistoryService history, SupportNotificationService notifications,
                                      PlatformTransactionManager transactions) {
        this.store = store;
        this.access = access;
        this.members = members;
        this.analysis = analysis;
        this.history = history;
        this.notifications = notifications;
        this.tx = new TransactionTemplate(transactions);
    }

    public record View(long id, LocalDate periodStart, LocalDate periodEndExclusive, Status status,
                       boolean canCancel, boolean requestedByMe, String analysisId, String errorCode) { }

    public View enqueue(long bookId, long userId, Request request) {
        return view(submit(bookId, userId, request, null), userId);
    }

    public Response runLegacy(long bookId, long userId, Request request) {
        // 기존 모바일 POST 응답 형식과 지문 검사를 보존하며 같은 DB 슬롯을 사용한다.
        var job = submit(bookId, userId, request, Instant.now().plus(LEASE));
        return execute(job, true);
    }

    private Job submit(long bookId, long userId, Request request, Instant leaseUntil) {
        var range = SpendingAnalysisPeriods.resolve(request);
        try {
            return Objects.requireNonNull(tx.execute(transaction -> {
                access.getAccessibleAccountBook(bookId, userId);
                return store.insert(bookId, userId, request, range, leaseUntil);
            }));
        } catch (DuplicateKeyException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ANALYSIS_IN_PROGRESS");
        }
    }

    public View current(long bookId, long userId) {
        access.getAccessibleAccountBook(bookId, userId);
        return store.active(bookId).or(() -> store.latestRequested(bookId, userId))
                .map(job -> view(job, userId)).orElse(null);
    }

    public View get(long bookId, long userId, long jobId) {
        access.getAccessibleAccountBook(bookId, userId);
        return view(find(bookId, jobId), userId);
    }

    public View cancel(long bookId, long userId, long jobId) {
        return tx.execute(transaction -> {
            // 요청자 본인이라도 현재 가계부 소유자가 아니면 취소할 수 없다.
            access.getAccessibleAccountBook(bookId, userId);
            if (!isOwner(bookId, userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ANALYSIS_CANCEL_FORBIDDEN");
            }

            var job = lock(bookId, jobId);
            store.cancel(job.id());
            return view(lock(bookId, jobId), userId);
        });
    }

    private Job find(long bookId, long jobId) {
        return store.find(bookId, jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ANALYSIS_JOB_NOT_FOUND"));
    }

    private Job lock(long bookId, long jobId) {
        return store.lock(bookId, jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ANALYSIS_JOB_NOT_FOUND"));
    }

    private boolean isOwner(long bookId, long userId) {
        return members.existsByAccountBook_IdAndUser_IdAndRoleAndDeletedFalse(bookId, userId, AccountBookMemberRole.OWNER);
    }

    private View view(Job job, long userId) {
        boolean canCancel = (job.status() == Status.QUEUED || job.status() == Status.RUNNING)
                && isOwner(job.bookId(), userId);
        return new View(job.id(), job.start(), job.endExclusive(), job.status(), canCancel,
                job.requestedBy() == userId, job.analysisId(), job.requestedBy() == userId ? job.errorCode() : null);
    }

    void expire() {
        store.expire(Instant.now());
    }

    void runNext() {
        var job = store.nextQueued().orElse(null);
        if (job != null && store.claim(job.id(), Instant.now().plus(LEASE))) {
            execute(job, false);
        }
    }

    private Response execute(Job job, boolean legacy) {
        try {
            var request = store.request(job.id());
            if (request == null) throw new CancellationException();

            // 공급자 호출은 트랜잭션 밖에서 진행하고, 취소·만료를 확인한 뒤에만 시작한다.
            var result = analysis.generate(job.bookId(), job.requestedBy(), request, legacy,
                    () -> store.canCallProvider(job.id(), Instant.now().plus(PROVIDER_BUDGET)));
            boolean committed = Boolean.TRUE.equals(tx.execute(transaction -> {
                access.getAccessibleAccountBook(job.bookId(), job.requestedBy());
                if (!store.complete(job.id(), result.analysisId(), Instant.now())) return false;

                // 이력, 완료 상태, 수신자 한 명의 알림을 한 번에 커밋한다.
                history.save(job.bookId(), job.requestedBy(), result);
                notifications.notifySpendingAnalysisCompleted(job.requestedBy(), job.bookId(), job.id(),
                        job.start(), job.endExclusive());
                return true;
            }));
            if (!committed) throw new CancellationException();
            return result;
        } catch (RuntimeException error) {
            String code = errorCode(error);
            store.finishWithoutResult(job.id(), code);
            if (!(error instanceof CancellationException)) {
                // 오류 진단에 요청 본문, 거래 데이터, 사용자나 가계부 번호를 남기지 않는다.
                log.warn("Spending analysis job failed: code={}, exceptionType={}", code, error.getClass().getSimpleName());
            }
            if (legacy) {
                if (error instanceof CancellationException) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "ANALYSIS_CANCELED");
                }
                throw error;
            }
            return null;
        }
    }

    private static String errorCode(RuntimeException error) {
        if (error instanceof ResponseStatusException status && status.getReason() != null) {
            return switch (status.getReason()) {
                case "SPENDING_ANALYSIS_UNAVAILABLE", "STALE_SNAPSHOT", "SPENDING_ANALYSIS_TOO_LARGE" -> status.getReason();
                default -> FAILED;
            };
        }
        return FAILED;
    }
}
