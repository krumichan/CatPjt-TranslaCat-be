package jp.co.translacat.domain.languagelearning.listening.architecture;

import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.listening.attempt.service.ListeningAttemptQueryService;
import jp.co.translacat.domain.languagelearning.listening.controller.ListeningDailySetController;
import jp.co.translacat.domain.languagelearning.listening.controller.ListeningDashboardController;
import jp.co.translacat.domain.languagelearning.listening.controller.ListeningSessionController;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningDailySetRepository;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningItemRepository;
import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningDailySetQueryService;
import jp.co.translacat.domain.languagelearning.listening.dashboard.service.ListeningDashboardQueryService;
import jp.co.translacat.domain.languagelearning.listening.evaluation.repository.ListeningTaskEvaluationRepository;
import jp.co.translacat.domain.languagelearning.listening.outbox.repository.ListeningOutboxEventRepository;
import jp.co.translacat.domain.languagelearning.listening.recommendation.repository.LearningRecommendationRepository;
import jp.co.translacat.domain.languagelearning.listening.response.repository.ListeningTaskResponseRepository;
import jp.co.translacat.domain.languagelearning.listening.session.repository.ListeningSessionRepository;
import jp.co.translacat.domain.languagelearning.listening.session.service.ListeningSessionQueryService;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListeningArchitectureTest {

    private static final List<Class<?>> REPOSITORIES = List.of(
            ListeningDailySetRepository.class,
            ListeningItemRepository.class,
            ListeningItemAttemptRepository.class,
            ListeningTaskEvaluationRepository.class,
            ListeningOutboxEventRepository.class,
            ListeningTaskResponseRepository.class,
            ListeningSessionRepository.class,
            LearningRecommendationRepository.class
    );

    private static final List<Class<?>> CONTROLLERS = List.of(
            ListeningDailySetController.class,
            ListeningDashboardController.class,
            ListeningSessionController.class
    );

    private static final List<Class<?>> QUERY_SERVICES = List.of(
            ListeningDailySetQueryService.class,
            ListeningAttemptQueryService.class,
            ListeningDashboardQueryService.class,
            ListeningSessionQueryService.class,
            ListeningPolicySettingQueryService.class
    );

    @Test
    void listeningRepositoriesDoNotUseStringBasedQueryAnnotations() {
        var annotatedMethods = REPOSITORIES.stream()
                .flatMap(repository -> List.of(repository.getDeclaredMethods())
                        .stream())
                .filter(method -> method.isAnnotationPresent(Query.class))
                .toList();

        assertThat(annotatedMethods).isEmpty();
    }

    @Test
    void controllersDependOnlyOnFacades() {
        var invalidFields = CONTROLLERS.stream()
                .flatMap(controller -> List.of(controller.getDeclaredFields())
                        .stream())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> !field.getType().getSimpleName()
                        .endsWith("Facade"))
                .toList();

        assertThat(invalidFields).isEmpty();
    }

    @Test
    void queryServicesDeclareReadOnlyTransactions() {
        var invalidServices = QUERY_SERVICES.stream()
                .filter(service -> {
                    Transactional transactional = service.getAnnotation(
                            Transactional.class
                    );
                    return transactional == null || !transactional.readOnly();
                })
                .toList();

        assertThat(invalidServices).isEmpty();
    }
}
