package jp.co.translacat.domain.languagelearning.listening.dashboard.facade;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.dashboard.service.ListeningDashboardQueryService;
import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningDailySetQueryService;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.recommendation.service.ListeningRecommendationCommandService;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ListeningDashboardFacade {

    private final ListeningDashboardQueryService dashboardQueryService;
    private final ListeningRecommendationCommandService recommendationCommandService;
    private final ListeningDailySetQueryService dailySetQueryService;
    private final LanguageLearningUserSettingQueryService userSettingQueryService;

    public ListeningApiContract.DashboardV3View dashboard(
            Long userId,
            LocalDate from,
            LocalDate to,
            ListeningTaskType taskType
    ) {
        var setting = userSettingQueryService.getOrCreateEntity(userId);
        userSettingQueryService.requireConfigured(setting);
        LocalDate resolvedTo = to == null
                ? userSettingQueryService.resolveToday(setting)
                : to;
        LocalDate resolvedFrom = from == null
                ? resolvedTo.minusDays(29)
                : from;
        String learningLanguage = setting.getLearningLanguage();
        List<ListeningApiContract.MetricProfileView> profiles =
                dashboardQueryService.profiles(
                        userId,
                        learningLanguage,
                        taskType
                );
        List<ListeningApiContract.MetricProfileView> recommendationProfiles =
                taskType == null
                        ? profiles
                        : dashboardQueryService.profiles(
                                userId,
                                learningLanguage,
                                null
                        );
        recommendationCommandService.refresh(
                userId,
                learningLanguage,
                recommendationProfiles
        );

        return new ListeningApiContract.DashboardV3View(
                learningLanguage,
                resolvedFrom,
                resolvedTo,
                profiles,
                dashboardQueryService.trends(
                        userId,
                        learningLanguage,
                        resolvedFrom,
                        resolvedTo,
                        taskType
                ),
                dashboardQueryService.recommendations(
                        userId,
                        learningLanguage
                )
        );
    }

    public void dismiss(Long userId, Long recommendationId) {
        recommendationCommandService.dismiss(userId, recommendationId);
    }

    public ListeningApiContract.PolicyView policy() {
        return dailySetQueryService.policy();
    }
}
