package jp.co.translacat.domain.accountbook.analysis.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JdbcSpendingAnalysisHistoryStore implements SpendingAnalysisHistoryStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Override
    public void save(Long bookId, Long userId, Response response) {
        // 기간이 같아도 INSERT로 새 스냅샷을 남긴다. 이전 결과는 갱신하지 않는다.
        try {
            jdbc.update("INSERT INTO account_book_spending_analysis (id, account_book_id, created_by, generated_at, result_json) VALUES (?, ?, ?, ?, ?)",
                    response.analysisId(), bookId, userId, Timestamp.from(response.generatedAt()), mapper.writeValueAsString(response));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not serialize analysis snapshot");
        }
    }

    @Override
    public List<Response> list(Long bookId, int page, int size) {
        return jdbc.query("SELECT result_json FROM account_book_spending_analysis WHERE account_book_id=? ORDER BY generated_at DESC,id DESC LIMIT ? OFFSET ?",
                (row, index) -> decode(row.getString(1)), bookId, size, Math.multiplyExact(page, size));
    }

    @Override
    public long count(Long bookId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM account_book_spending_analysis WHERE account_book_id=?", Long.class, bookId);
        return count == null ? 0 : count;
    }

    @Override
    public Optional<Response> find(Long bookId, String analysisId) {
        return jdbc.query("SELECT result_json FROM account_book_spending_analysis WHERE account_book_id=? AND id=?",
                (row, index) -> decode(row.getString(1)), bookId, analysisId).stream().findFirst();
    }

    private Response decode(String json) {
        try {
            return mapper.readValue(json, Response.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not read saved analysis snapshot");
        }
    }
}
