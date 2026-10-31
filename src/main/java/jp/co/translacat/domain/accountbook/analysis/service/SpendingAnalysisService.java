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

@Service
@RequiredArgsConstructor
public class SpendingAnalysisService {
    private final SpendingAnalysisSnapshotService snapshots;
    private final AiServerClient ai;

    public Response analyze(Long bookId, Long userId, Request request) {
        var prepared = snapshots.prepare(bookId, userId, request);
        var input = prepared.input();
        if (input.transactionCount() == 0) {
            String empty = switch (request.locale()) {
                case "ja" -> "選択した期間に支出の記録がありません。";
                case "en" -> "No expenses were recorded in this period.";
                default -> "선택한 기간에 기록된 지출이 없습니다.";
            };
            return response(prepared, empty, List.of());
        }

        // 공급자 호출은 한 번만 수행하며 재분석은 사용자의 새 요청으로만 실행한다.
        AiResponse generated;
        try {
            generated = ai.callSpendingAnalysis(input);
        } catch (RuntimeException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SPENDING_ANALYSIS_UNAVAILABLE");
        }
        var findings = validate(generated, prepared);

        // 분석 중 변경되거나 접근권한을 잃은 거래에 이전 결과를 연결하지 않는다.
        var current = snapshots.current(bookId, userId, request.periodType(), request.year(), request.month());
        if (!prepared.fingerprint().equals(current.snapshotFingerprint())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "STALE_SNAPSHOT");
        }
        return response(prepared, generated.summary().strip(), findings);
    }

    private Response response(SpendingAnalysisSnapshotService.Prepared prepared, String summary, List<Finding> findings) {
        var input = prepared.input();
        return new Response(input.periodType(), input.year(), input.month(), input.periodStart(), input.periodEndExclusive(),
                input.currencyCode(), input.decimalPlaces(), input.totalExpense(), input.transactionCount(), input.coverage(),
                summary, findings, input.comment(), Instant.now(), prepared.fingerprint());
    }

    private List<Finding> validate(AiResponse generated, SpendingAnalysisSnapshotService.Prepared prepared) {
        if (generated == null || !validText(generated.summary(), 2000) || generated.findings() == null
                || generated.findings().size() > 8) throw invalid();
        List<Finding> findings = new ArrayList<>();
        for (var finding : generated.findings()) {
            if (finding == null || !validText(finding.title(), 160) || !validText(finding.reason(), 2000)
                    || !validText(finding.action(), 1200) || !optionalText(finding.contextQuestion(), 500)
                    || !optionalText(finding.uncertainty(), 500) || finding.evidenceTransactionRefs() == null
                    || finding.evidenceTransactionRefs().isEmpty() || finding.evidenceTransactionRefs().size() > 20) throw invalid();
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
            findings.add(new Finding("finding-" + (findings.size() + 1), finding.title().strip(), finding.reason().strip(),
                    finding.action().strip(), normalized(finding.contextQuestion()), normalized(finding.uncertainty()),
                    amount.toPlainString(), List.copyOf(evidence)));
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
