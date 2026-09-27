package jp.co.translacat.domain.languagelearning.dashboard.service;

import jp.co.translacat.domain.languagelearning.dashboard.dto.response.DashboardResponseDto;
import jp.co.translacat.domain.languagelearning.dashboard.port.OverviewGateway;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;

/**
 * 외부 Controller/DTO는 보존하고 집계·기간·출처 판정은 LL에 위임한다.
 */
@Service
@RequiredArgsConstructor
public class LanguageLearningDashboardQueryService {
    private final OverviewGateway overview;

    public DashboardResponseDto get(Long userId, LocalDate from, LocalDate to, String sourceValue,
                                    ListeningTaskType taskType) {
        var query = new LinkedHashMap<String, Object>();
        query.put("from", from);
        query.put("to", to);
        query.put("source", sourceValue);
        query.put("taskType", taskType);
        return overview.get(userId, "/dashboard", query, DashboardResponseDto.class);
    }
}
