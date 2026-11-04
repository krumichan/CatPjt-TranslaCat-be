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
    private static final int MAX_TRANSACTIONS = 6000;
    private static final int MAX_TEXT_CHARACTERS = 2_000_000;
    private final AccountBookAccessService access;
    private final JPAQueryFactory queries;

    public record Prepared(AiRequest input, Map<String, AccountBookTransactionResponseDto> evidence,
                           String fingerprint, Map<String, AccountBookTransactionResponseDto> historicalEvidence) {
        public Prepared(AiRequest input, Map<String, AccountBookTransactionResponseDto> evidence, String fingerprint) {
            this(input, evidence, fingerprint, Map.of());
        }
    }
    record Period(LocalDate start, LocalDate end) { }
    private record Candidate(long id, LocalDate date, int textCharacters) { }
    private record Scan(String fingerprint, long count, BigDecimal total, List<Candidate> candidates) { }

    public SnapshotInfo current(Long bookId, Long userId, PeriodType type, int year, Integer month) {
        return current(bookId, userId, new Request(type, year, month, "ko", null));
    }

    public SnapshotInfo current(Long bookId, Long userId, Request request) {
        // 사용자 권한과 통화를 함께 확인하고 전체 기간의 변경 여부를 계산한다.
        var book = access.getAccessibleAccountBook(bookId, userId);
        var range = SpendingAnalysisPeriods.resolve(request);
        var period = new Period(range.start(), range.end());
        var scan = scan(bookId, period, request.periodType(), book.getCurrency().getCode(), book.getCurrency().getDecimalPlaces(), false);
        var previous = totals(bookId, previousStart(period, request.periodType()), period.start());
        var history = scan(bookId, new Period(period.start().minusMonths(12), period.start()), PeriodType.YEARLY,
                book.getCurrency().getCode(), book.getCurrency().getDecimalPlaces(), false);
        return new SnapshotInfo(fingerprint(scan, previous, history), scan.count(), scan.total().toPlainString());
    }

    public Prepared prepare(Long bookId, Long userId, Request request) {
        // 원본 거래는 읽기 트랜잭션 안에서 스냅샷으로 바꾸고 외부 AI 호출 전에 경계를 끝낸다.
        var book = access.getAccessibleAccountBook(bookId, userId);
        var range = SpendingAnalysisPeriods.resolve(request);
        var period = new Period(range.start(), range.end());
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
        var previousStart = previousStart(period, request.periodType());
        var previous = totals(bookId, previousStart, period.start());
        var previousPeriod = new PreviousPeriod(previousStart, period.start(), previous.total().toPlainString(),
                previous.count(), previous.count() > 0 && previous.total().signum() > 0
                && !period.end().isAfter(LocalDate.now(ZoneId.of("Asia/Seoul"))));
        // 선택 기간 앞의 12개월만 문맥으로 읽는다. 이후 거래를 앞선 달 분석에 넣지 않는다.
        var history = scan(bookId, new Period(period.start().minusMonths(12), period.start()), PeriodType.YEARLY, currency, decimals, true);
        var historicalEvidence = loadHistory(bookId, history.candidates());
        var historicalTransactions = historicalEvidence.entrySet().stream().map(entry -> {
            var item = entry.getValue();
            return new AiTransaction(entry.getKey(), item.transactionDate(), item.title(), item.storeName(), item.category(),
                    item.memo(), item.amount().toPlainString(), item.sourceType() == null ? null : item.sourceType().name());
        }).toList();
        var input = new AiRequest(request.periodType(), request.year(), request.month(), request.locale(), currency,
                decimals, period.start(), period.end(), scan.total().toPlainString(), scan.count(), coverage,
                previousPeriod, normalizedComment(request.comment()), List.copyOf(transactions), request.quarter(), request.half(),
                historicalTransactions, List.of());
        return new Prepared(input, Collections.unmodifiableMap(evidence), fingerprint(scan, previous, history), historicalEvidence);
    }

    private record Totals(long count, BigDecimal total) { }
    private static String fingerprint(Scan scan, Totals previous, Scan history) {
        var digest = digest();
        addHash(digest, scan.fingerprint(), previous.count(), previous.total().toPlainString(), history.fingerprint());
        return HexFormat.of().formatHex(digest.digest());
    }

    private Map<String, AccountBookTransactionResponseDto> loadHistory(Long bookId, List<Candidate> candidates) {
        if (candidates.isEmpty()) return Map.of();
        var ids = candidates.stream().map(Candidate::id).toList();
        var loaded = queries.selectFrom(accountBookTransaction).where(accountBookTransaction.accountBook.id.eq(bookId),
                accountBookTransaction.id.in(ids)).orderBy(accountBookTransaction.transactionDate.asc(), accountBookTransaction.id.asc()).fetch();
        Map<String, AccountBookTransactionResponseDto> result = new LinkedHashMap<>();
        for (var item : loaded) result.put("tx-" + (100001 + result.size()), AccountBookTransactionResponseDto.from(item));
        return Collections.unmodifiableMap(result);
    }

    private static LocalDate previousStart(Period period, PeriodType type) {
        return switch (type) {
            case MONTHLY -> period.start().minusMonths(1);
            case QUARTERLY -> period.start().minusMonths(3);
            case HALF_YEARLY -> period.start().minusMonths(6);
            case YEARLY -> period.start().minusYears(1);
            case CUSTOM -> period.start().minusDays(java.time.temporal.ChronoUnit.DAYS.between(period.start(), period.end()));
        };
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
                int bucket = type != PeriodType.MONTHLY ? date.getYear() * 12 + date.getMonthValue() : 0;
                var queue = buckets.computeIfAbsent(bucket, ignored -> new PriorityQueue<>(order));
                var candidate = new Candidate(lastId, date, textLength);
                if (queue.size() < quota) queue.add(candidate);
                else if (order.compare(candidate, queue.peek()) > 0) {
                    queue.remove();
                    queue.add(candidate);
                }
            }
        }

        // 월별 후보를 보존하고 문자/건수 한도 이내인 기간에는 전건을 포함한다.
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
        var range = SpendingAnalysisPeriods.resolve(new Request(type, year, month, "ko", null));
        return new Period(range.start(), range.end());
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
