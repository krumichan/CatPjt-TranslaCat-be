package jp.co.translacat.domain.accountbook.analysis.repository;

import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.Response;
import java.util.List;
import java.util.Optional;

public interface SpendingAnalysisHistoryStore {
    void save(Long bookId, Long userId, Response response);
    List<Response> list(Long bookId, int page, int size);
    long count(Long bookId);
    Optional<Response> find(Long bookId, String analysisId);
}
