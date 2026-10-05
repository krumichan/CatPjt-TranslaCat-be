package jp.co.translacat.domain.languagelearning.history.service;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.dashboard.port.OverviewGateway;
import jp.co.translacat.domain.languagelearning.history.dto.response.LearningHistoryDetailResponseDto;
import jp.co.translacat.domain.languagelearning.history.dto.response.LearningHistoryItemResponseDto;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 이력의 출처 선택·정렬·상태·소유권과 원본 조회는 LL이 처리한다.
 */
@Service
@RequiredArgsConstructor
public class LearningHistoryQueryService {
    private final OverviewGateway overview;

    public JsonNode getEvidence(Long userId, String source, String learningLanguage, String from, String to,
                                String resultKind, String policyVersion, String cursor, String limit) {
        // 필터·페이지·정책의 유효성 및 기본값은 결과 소유자인 LL이 결정한다.
        var query = new LinkedHashMap<String, Object>();
        query.put("source", source);
        query.put("learningLanguage", learningLanguage);
        query.put("from", from);
        query.put("to", to);
        query.put("resultKind", resultKind);
        query.put("policyVersion", policyVersion);
        query.put("cursor", cursor);
        query.put("limit", limit);
        return overview.get(userId, "/evidence", query, JsonNode.class);
    }

    public List<LearningHistoryItemResponseDto> getHistory(Long userId, LearningSource source, String period,
                                                           String status) {
        return getHistory(userId, source, period, status, null);
    }

    public List<LearningHistoryItemResponseDto> getHistory(Long userId, LearningSource source, String period,
                                                           String status, ListeningTaskType taskType) {
        var query = new LinkedHashMap<String, Object>();
        query.put("source", source);
        query.put("period", period);
        query.put("status", status);
        query.put("taskType", taskType);
        return overview.list(userId, "/history", query, LearningHistoryItemResponseDto.class);
    }

    public LearningHistoryDetailResponseDto getDetail(Long userId, String activityId) {
        return overview.get(userId, "/history/" + activityId, Map.of(), LearningHistoryDetailResponseDto.class);
    }
}
