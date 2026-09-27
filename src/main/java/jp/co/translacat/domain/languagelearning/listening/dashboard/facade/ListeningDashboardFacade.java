package jp.co.translacat.domain.languagelearning.listening.dashboard.facade;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.daily.facade.ListeningDailySetFacade;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.model.ListeningReportSnapshot;
import jp.co.translacat.domain.languagelearning.listening.port.ListeningGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ListeningDashboardFacade {
    private final ListeningGateway gateway;
    private final ListeningDailySetFacade dailySetFacade;

    public ListeningApiContract.DashboardView dashboard(Long userId, LocalDate from, LocalDate to,
                                                        ListeningTaskType taskType) {
        return gateway.get(userId, range("/dashboard", from, to, taskType), ListeningApiContract.DashboardView.class);
    }

    public List<ListeningApiContract.MetricTrendView> metricTrends(Long userId, String language, LocalDate from,
                                                                   LocalDate to, ListeningTaskType taskType) {
        String path = UriComponentsBuilder.fromUriString(range("/metric-trends", from, to, taskType))
                .queryParam("learningLanguage", language).build().encode().toUriString();
        return gateway.list(userId, path, ListeningApiContract.MetricTrendView.class);
    }

    public ListeningReportSnapshot report(Long userId, LocalDate from, LocalDate to, ListeningTaskType taskType) {
        return gateway.get(userId, range("/report", from, to, taskType), ListeningReportSnapshot.class);
    }

    public ListeningApiContract.HistoryDetailView history(Long userId, Long sessionId) {
        return gateway.get(userId, "/sessions/" + sessionId + "/history", ListeningApiContract.HistoryDetailView.class);
    }

    public void dismiss(Long userId, Long recommendationId) {
        gateway.post(userId, "/recommendations/" + recommendationId + "/dismiss", null, Void.class);
    }

    public ListeningApiContract.PolicyView policy() {
        return dailySetFacade.policy();
    }

    private String range(String path, LocalDate from, LocalDate to, ListeningTaskType taskType) {
        var builder = UriComponentsBuilder.fromPath(path);
        if (from != null) builder.queryParam("from", from);
        if (to != null) builder.queryParam("to", to);
        if (taskType != null) builder.queryParam("taskType", taskType);
        return builder.build().encode().toUriString();
    }
}
