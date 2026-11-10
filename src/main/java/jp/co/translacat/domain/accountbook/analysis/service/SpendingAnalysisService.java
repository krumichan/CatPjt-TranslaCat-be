package jp.co.translacat.domain.accountbook.analysis.service;

import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.*;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionResponseDto;
import jp.co.translacat.infrastructure.client.ai.server.AiServerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

@Service
@RequiredArgsConstructor
public class SpendingAnalysisService {
    private static final int MAX_ANALYSIS_FINDINGS = 40;

    private final SpendingAnalysisSnapshotService snapshots;
    private final AiServerClient ai;
    private final SpendingAnalysisHistoryService history;

    Response generate(Long bookId, Long userId, Request request, boolean requireCurrent, BooleanSupplier canCallProvider) {
        // 결과는 작업을 실행할 때 읽은 거래와 그 기준 시점을 보존한다.
        var snapshotAt = Instant.now();
        var prepared = snapshots.prepare(bookId, userId, request);
        var base = prepared.input();
        var advice = history.previousAdvice(bookId, userId, base.periodStart());
        var input = new AiRequest(base.periodType(), base.year(), base.month(), base.locale(), base.currencyCode(),
                base.decimalPlaces(), base.periodStart(), base.periodEndExclusive(), base.totalExpense(), base.transactionCount(),
                base.coverage(), base.previousPeriod(), base.comment(), base.transactions(), base.quarter(), base.half(),
                base.historicalTransactions(), advice);
        if (input.transactionCount() == 0) {
            String empty = switch (request.locale()) {
                case "ja" -> "選択した期間に支出の記録がありません。";
                case "en" -> "No expenses were recorded in this period.";
                default -> "선택한 기간에 기록된 지출이 없습니다.";
            };
            return response(prepared, empty, List.of(), "recurring-choice-v6", snapshotAt);
        }

        // 공급자 호출은 한 번만 수행하며 재분석은 사용자의 새 요청으로만 실행한다.
        if (!canCallProvider.getAsBoolean()) throw new CancellationException();
        AiResponse generated;
        try {
            generated = ai.callSpendingAnalysis(input);
        } catch (RuntimeException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SPENDING_ANALYSIS_UNAVAILABLE");
        }
        var findings = validate(generated, prepared);

        // 기존 동기 클라이언트는 이전 계약대로 변경된 지문을 거절한다.
        // 백그라운드 결과는 생성 당시 거래를 보존하며 화면에서 현재 지문과 대조한다.
        if (requireCurrent) {
            var current = snapshots.current(bookId, userId, request);
            if (!prepared.fingerprint().equals(current.snapshotFingerprint())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "STALE_SNAPSHOT");
            }
        }
        return response(prepared, generated.summary().strip(), findings,
                generated.policyVersion() == null ? "legacy-v2" : generated.policyVersion(), snapshotAt);
    }

    private Response response(SpendingAnalysisSnapshotService.Prepared prepared, String summary, List<Finding> findings,
                              String policyVersion, Instant snapshotAt) {
        var input = prepared.input();
        return new Response(input.periodType(), input.year(), input.month(), input.periodStart(), input.periodEndExclusive(),
                input.currencyCode(), input.decimalPlaces(), input.totalExpense(), input.transactionCount(), input.coverage(),
                summary, findings, input.comment(), Instant.now(), prepared.fingerprint(), UUID.randomUUID().toString(),
                input.quarter(), input.half(), policyVersion, SpendingAnalysisSavings.combine(findings), snapshotAt);
    }

    private List<Finding> validate(AiResponse generated, SpendingAnalysisSnapshotService.Prepared prepared) {
        if (generated == null || !validText(generated.summary(), 2000) || generated.findings() == null
                || generated.findings().size() > MAX_ANALYSIS_FINDINGS) throw invalid();
        List<Finding> findings = new ArrayList<>();
        var used = new HashSet<String>();
        for (var finding : generated.findings()) {
            if (finding == null || !validText(finding.title(), 160) || !validText(finding.reason(), 2000)
                    || !validText(finding.action(), 1200) || !optionalText(finding.contextQuestion(), 500)
                    || !optionalText(finding.uncertainty(), 500) || finding.evidenceTransactionRefs() == null
                    || finding.evidenceTransactionRefs().isEmpty() || finding.evidenceTransactionRefs().size() > 6000) throw invalid();
            var seen = new HashSet<String>();
            List<AccountBookTransactionResponseDto> evidence = new ArrayList<>();
            BigDecimal amount = BigDecimal.ZERO;
            for (var ref : finding.evidenceTransactionRefs()) {
                if (ref == null) throw invalid();
                var transaction = prepared.evidence().get(ref);
                if (!seen.add(ref) || transaction == null) throw invalid();
                evidence.add(transaction);
                amount = amount.add(transaction.amount());
            }
            var historical = new ArrayList<AccountBookTransactionResponseDto>();
            if (finding.historicalTransactionRefs() != null) {
                var priorSeen = new HashSet<String>();
                for (var ref : finding.historicalTransactionRefs()) {
                    var transaction = prepared.historicalEvidence().get(ref);
                    if (transaction == null || !priorSeen.add(ref)) throw invalid();
                    historical.add(transaction);
                }
            }
            if (!optionalText(finding.opinion(), 1200)) throw invalid();
            var targets = new ArrayList<AccountBookTransactionResponseDto>();
            if (finding.savingsTransactionRefs() != null) {
                for (var ref : finding.savingsTransactionRefs()) {
                    if (!seen.contains(ref) || !used.add(ref)) throw invalid();
                    targets.add(prepared.evidence().get(ref));
                }
            }
            var saving = SpendingAnalysisSavings.calculate(finding.savings(), targets, prepared.input().decimalPlaces(),
                    prepared.input().coverage().isPartial(), prepared.input().comment());
            findings.add(new Finding("finding-" + (findings.size() + 1), finding.title().strip(), finding.reason().strip(),
                    finding.action().strip(), null, normalized(finding.uncertainty()), amount.toPlainString(),
                    List.copyOf(evidence), normalized(finding.opinion()), List.copyOf(historical), saving, saving == null ? List.of() : List.copyOf(targets)));
        }
        return List.copyOf(findings);
    }

    private static boolean validText(String value, int max) { return value != null && !value.isBlank() && value.length() <= max; }
    private static boolean optionalText(String value, int max) { return value == null || value.length() <= max; }
    private static String normalized(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SPENDING_ANALYSIS_INVALID_RESPONSE");
    }
}
