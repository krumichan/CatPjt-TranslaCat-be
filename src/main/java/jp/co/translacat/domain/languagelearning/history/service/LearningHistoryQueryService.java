package jp.co.translacat.domain.languagelearning.history.service;

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
