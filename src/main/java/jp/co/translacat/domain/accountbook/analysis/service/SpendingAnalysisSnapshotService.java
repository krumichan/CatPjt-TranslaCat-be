package jp.co.translacat.domain.accountbook.analysis.service;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.*;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionResponseDto;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;

import static jp.co.translacat.domain.accountbook.transaction.entity.QAccountBookTransaction.accountBookTransaction;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpendingAnalysisSnapshotService {
    private static final int PAGE_SIZE = 500;
    private static final int MAX_TRANSACTIONS = 600;
    private static final int MAX_TEXT_CHARACTERS = 200_000;
    private final AccountBookAccessService access;
    private final JPAQueryFactory queries;

    public record Prepared(AiRequest input, Map<String, AccountBookTransactionResponseDto> evidence,
                           String fingerprint) { }
    record Period(LocalDate start, LocalDate end) { }
    private record Candidate(long id, LocalDate date, int textCharacters) { }
    private record Scan(String fingerprint, long count, BigDecimal total, List<Candidate> candidates) { }

    public SnapshotInfo current(Long bookId, Long userId, PeriodType type, int year, Integer month) {
        // 사용자 권한과 통화를 함께 확인하고 전체 기간의 변경 여부를 계산한다.
        var book = access.getAccessibleAccountBook(bookId, userId);
        var period = period(type, year, month);
        var scan = scan(bookId, period, type, book.getCurrency().getCode(), book.getCurrency().getDecimalPlaces(), false);
        var previous = totals(bookId, type == PeriodType.MONTHLY ? period.start().minusMonths(1) : period.start().minusYears(1), period.start());
        return new SnapshotInfo(fingerprint(scan, previous), scan.count(), scan.total().toPlainString());
    }

    public Prepared prepare(Long bookId, Long userId, Request request) {
        // 원본 거래는 읽기 트랜잭션 안에서 스냅샷으로 바꾸고 외부 AI 호출 전에 경계를 끝낸다.
        var book = access.getAccessibleAccountBook(bookId, userId);
        var period = period(request.periodType(), request.year(), request.month());
        String currency = book.getCurrency().getCode();
        int decimals = book.getCurrency().getDecimalPlaces();
        var scan = scan(bookId, period, request.periodType(), currency, decimals, true);
        var selected = new ArrayList<Candidate>();
        int characters = 0;

        // 메모 원문은 자르지 않고 거래 단위로 문자 예산 안에 포함한다.
        for (var candidate : scan.candidates()) {
            if (characters + candidate.textCharacters() > MAX_TEXT_CHARACTERS) continue;
            selected.add(candidate);
            characters += candidate.textCharacters();
        }

        // 실제 거래 ID는 공급자에 보내지 않고 요청 안에서만 유효한 참조로 대체한다.
        Map<String, AccountBookTransactionResponseDto> evidence = new LinkedHashMap<>();
        List<AiTransaction> transactions = new ArrayList<>();
        if (!selected.isEmpty()) {
            var ids = selected.stream().map(Candidate::id).toList();
            var loaded = queries.selectFrom(accountBookTransaction)
                    .where(accountBookTransaction.accountBook.id.eq(bookId), accountBookTransaction.id.in(ids))
                    .fetch();
            Map<Long, AccountBookTransactionResponseDto> byId = new HashMap<>();
            loaded.forEach(item -> byId.put(item.getId(), AccountBookTransactionResponseDto.from(item)));
            for (var candidate : selected) {
                var item = byId.get(candidate.id());
                if (item == null) throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT, "STALE_SNAPSHOT");
                String ref = "tx-" + (transactions.size() + 1);
                evidence.put(ref, item);
                transactions.add(new AiTransaction(ref, item.transactionDate(), item.title(), item.storeName(),
                        item.category(), item.memo(), item.amount().toPlainString(),
                        item.sourceType() == null ? null : item.sourceType().name()));
            }
        }

        boolean partial = transactions.size() != scan.count();
        String reason = !partial ? null : selected.size() < scan.candidates().size()
                ? "TEXT_BUDGET" : "TRANSACTION_LIMIT";
        var coverage = new Coverage(transactions.size(), scan.count(), partial, reason);
        var previousStart = request.periodType() == PeriodType.MONTHLY ? period.start().minusMonths(1) : period.start().minusYears(1);
        var previous = totals(bookId, previousStart, period.start());
        var previousPeriod = new PreviousPeriod(previousStart, period.start(), previous.total().toPlainString(),
                previous.count(), previous.count() > 0 && previous.total().signum() > 0
                && !period.end().isAfter(LocalDate.now(ZoneId.of("Asia/Seoul"))));
        var input = new AiRequest(request.periodType(), request.year(), request.month(), request.locale(), currency,
                decimals, period.start(), period.end(), scan.total().toPlainString(), scan.count(), coverage,
                previousPeriod, normalizedComment(request.comment()), List.copyOf(transactions));
        return new Prepared(input, Collections.unmodifiableMap(evidence), fingerprint(scan, previous));
    }

    private record Totals(long count, BigDecimal total) { }
    private static String fingerprint(Scan scan, Totals previous) {
        var digest = digest();
        addHash(digest, scan.fingerprint(), previous.count(), previous.total().toPlainString());
        return HexFormat.of().formatHex(digest.digest());
    }
    private Totals totals(Long bookId, LocalDate start, LocalDate end) {
        var row = queries.select(accountBookTransaction.id.count(), accountBookTransaction.amount.sum())
                .from(accountBookTransaction).where(accountBookTransaction.accountBook.id.eq(bookId),
                        accountBookTransaction.type.eq(AccountBookTransactionType.EXPENSE),
                        accountBookTransaction.transactionDate.goe(start), accountBookTransaction.transactionDate.lt(end)).fetchOne();
        return new Totals(row == null ? 0 : Objects.requireNonNullElse(row.get(0, Long.class), 0L),
                row == null ? BigDecimal.ZERO : Objects.requireNonNullElse(row.get(1, BigDecimal.class), BigDecimal.ZERO));
    }

    private Scan scan(Long bookId, Period period, PeriodType type, String currency, int decimals, boolean sample) {
        var digest = digest();
        addHash(digest, bookId, period.start(), period.end(), currency, decimals);
        Map<Integer, PriorityQueue<Candidate>> buckets = new TreeMap<>();
        Comparator<Candidate> order = Comparator.comparing(Candidate::date).thenComparingLong(Candidate::id);
        long lastId = 0;
        long count = 0;
        BigDecimal total = BigDecimal.ZERO;
        int quota = MAX_TRANSACTIONS;

        // 전체 거래를 제한된 크기의 tuple로 읽어 메모 수정도 감지하되 엔티티를 무제한 보관하지 않는다.
        while (true) {
            List<Tuple> rows = queries.select(accountBookTransaction.id, accountBookTransaction.transactionDate,
                            accountBookTransaction.amount, accountBookTransaction.title, accountBookTransaction.storeName,
                            accountBookTransaction.category, accountBookTransaction.memo, accountBookTransaction.updatedAt,
                            accountBookTransaction.sourceType)
                    .from(accountBookTransaction).where(accountBookTransaction.accountBook.id.eq(bookId),
                            accountBookTransaction.type.eq(AccountBookTransactionType.EXPENSE),
                            accountBookTransaction.transactionDate.goe(period.start()), accountBookTransaction.transactionDate.lt(period.end()),
                            accountBookTransaction.id.gt(lastId)).orderBy(accountBookTransaction.id.asc()).limit(PAGE_SIZE).fetch();
            if (rows.isEmpty()) break;
            for (var row : rows) {
                lastId = Objects.requireNonNull(row.get(accountBookTransaction.id));
                var date = Objects.requireNonNull(row.get(accountBookTransaction.transactionDate));
                var amount = Objects.requireNonNull(row.get(accountBookTransaction.amount));
                addHash(digest, lastId, date, amount.toPlainString(), row.get(accountBookTransaction.title),
                        row.get(accountBookTransaction.storeName), row.get(accountBookTransaction.category),
                        row.get(accountBookTransaction.memo), row.get(accountBookTransaction.updatedAt),
                        row.get(accountBookTransaction.sourceType));
                count++;
                total = total.add(amount);
                if (!sample) continue;
                int textLength = 100;
                for (int i = 3; i <= 6; i++) {
                    String value = row.get(i, String.class);
                    if (value != null) textLength += encodedTextLength(value);
                }
                int bucket = type == PeriodType.YEARLY ? date.getMonthValue() : 0;
                var queue = buckets.computeIfAbsent(bucket, ignored -> new PriorityQueue<>(order));
                var candidate = new Candidate(lastId, date, textLength);
                if (queue.size() < quota) queue.add(candidate);
                else if (order.compare(candidate, queue.peek()) > 0) {
                    queue.remove();
                    queue.add(candidate);
                }
            }
        }

        // 월별 후보를 충분히 보존한 뒤 전체 600건만 번갈아 선택한다.
        // 전체 거래가 600건 이하인 연도에는 특정 월의 거래를 불필요하게 누락하지 않는다.
        var monthLists = buckets.values().stream().map(queue -> queue.stream().sorted(order.reversed()).toList()).toList();
        var candidates = new ArrayList<Candidate>();
        for (int index = 0; index < quota && candidates.size() < MAX_TRANSACTIONS; index++) {
            for (var list : monthLists) {
                if (index < list.size()) candidates.add(list.get(index));
                if (candidates.size() == MAX_TRANSACTIONS) break;
            }
        }
        return new Scan(HexFormat.of().formatHex(digest.digest()), count, total, candidates);
    }

    static Period period(PeriodType type, int year, Integer month) {
        if (type == null || year < 2000 || year > 9998 || (type == PeriodType.MONTHLY
                ? month == null || month < 1 || month > 12 : month != null)) {
            throw new IllegalArgumentException("Invalid spending analysis period.");
        }
        LocalDate start = type == PeriodType.MONTHLY ? YearMonth.of(year, month).atDay(1) : LocalDate.of(year, 1, 1);
        return new Period(start, type == PeriodType.MONTHLY ? start.plusMonths(1) : start.plusYears(1));
    }

    static String normalizedComment(String comment) { return comment == null || comment.isBlank() ? null : comment.strip(); }
    private static int encodedTextLength(String value) {
        int length = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            length += character < ' ' ? 6 : character == '"' || character == '\\' ? 2 : 1;
        }
        return length;
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private static void addHash(MessageDigest digest, Object... values) {
        for (var value : values) {
            if (value == null) {
                digest.update(ByteBuffer.allocate(4).putInt(-1).array());
                continue;
            }
            byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
            digest.update(bytes);
        }
    }
}
