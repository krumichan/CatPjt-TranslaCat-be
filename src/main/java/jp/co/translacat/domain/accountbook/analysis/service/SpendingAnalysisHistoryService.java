package jp.co.translacat.domain.accountbook.analysis.service;

import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.*;
import jp.co.translacat.domain.accountbook.analysis.repository.SpendingAnalysisHistoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SpendingAnalysisHistoryService {
    private final AccountBookAccessService access;
    private final SpendingAnalysisHistoryStore store;

    public void save(Long bookId, Long userId, Response response) {
        // 저장 시에도 접근권한을 다시 확인한다. 외부 AI 호출을 DB 트랜잭션에 포함하지 않는다.
        access.getAccessibleAccountBook(bookId, userId);
        store.save(bookId, userId, response);
    }

    public HistoryPage list(Long bookId, Long userId, int page, int size) {
        access.getAccessibleAccountBook(bookId, userId);
        if (page < 0 || page > 100000 || size < 1 || size > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_HISTORY_PAGE");
        var items = store.list(bookId, page, size).stream().map(result -> new HistoryItem(result.analysisId(),
                result.periodType(), result.periodStart(), result.periodEndExclusive(), result.currencyCode(),
                result.totalExpense(), result.summary(), result.contextComment(), result.generatedAt(), result.policyVersion(), result.savings())).toList();
        return new HistoryPage(items, store.count(bookId), page, size);
    }

    public Response find(Long bookId, Long userId, String analysisId) {
        access.getAccessibleAccountBook(bookId, userId);
        return store.find(bookId, analysisId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND"));
    }

    public java.util.List<String> previousAdvice(Long bookId, Long userId, java.time.LocalDate before) {
        access.getAccessibleAccountBook(bookId, userId);
        // 미래/겹치는 기간의 보고서는 과거 분석 문맥으로 되돌려 넣지 않는다.
        return store.list(bookId, 0, 50).stream().filter(result -> !result.periodEndExclusive().isAfter(before))
                .limit(6).map(result -> result.periodStart() + "~" + result.periodEndExclusive() + ": "
                        + result.findings().stream().map(finding -> finding.title() + ": " + finding.action()).collect(java.util.stream.Collectors.joining("; ")))
                .map(text -> text.length() > 4000 ? text.substring(0, 4000) : text).toList();
    }
}
