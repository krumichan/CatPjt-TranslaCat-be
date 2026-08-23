package jp.co.translacat.domain.languagelearning.listening.architecture;

import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.listening.attempt.service.ListeningAttemptQueryService;
import jp.co.translacat.domain.languagelearning.listening.controller.ListeningDailySetController;
import jp.co.translacat.domain.languagelearning.listening.controller.ListeningDashboardController;
import jp.co.translacat.domain.languagelearning.listening.controller.ListeningSessionController;
import jp.co.translacat.domain.languagelearning.listening.daily.facade.ListeningDailySetFacade;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningDailySetRepository;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningItemRepository;
import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningDailySetQueryService;
import jp.co.translacat.domain.languagelearning.listening.dashboard.facade.ListeningDashboardFacade;
import jp.co.translacat.domain.languagelearning.listening.dashboard.service.ListeningDashboardQueryService;
import jp.co.translacat.domain.languagelearning.listening.evaluation.repository.ListeningTaskEvaluationRepository;
import jp.co.translacat.domain.languagelearning.listening.outbox.repository.ListeningOutboxEventRepository;
import jp.co.translacat.domain.languagelearning.listening.recommendation.repository.LearningRecommendationRepository;
import jp.co.translacat.domain.languagelearning.listening.response.repository.ListeningTaskResponseRepository;
import jp.co.translacat.domain.languagelearning.listening.session.facade.ListeningSessionFacade;
import jp.co.translacat.domain.languagelearning.listening.session.repository.ListeningSessionRepository;
import jp.co.translacat.domain.languagelearning.listening.session.service.ListeningSessionQueryService;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListeningLayeringConventionTest {

    private static final List<Class<?>> REPOSITORIES = List.of(
            ListeningDailySetRepository.class,
            ListeningItemRepository.class,
            ListeningItemAttemptRepository.class,
            ListeningTaskEvaluationRepository.class,
            LearningRecommendationRepository.class,
            ListeningOutboxEventRepository.class,
            ListeningTaskResponseRepository.class,
            ListeningSessionRepository.class
    );

    private static final List<Class<?>> CONTROLLERS = List.of(
            ListeningDailySetController.class,
            ListeningDashboardController.class,
            ListeningSessionController.class
    );

    private static final List<Class<?>> FACADES = List.of(
            ListeningDailySetFacade.class,
            ListeningDashboardFacade.class,
            ListeningSessionFacade.class
    );

    private static final List<Class<?>> QUERY_SERVICES = List.of(
            ListeningAttemptQueryService.class,
            ListeningDailySetQueryService.class,
            ListeningDashboardQueryService.class,
            ListeningSessionQueryService.class,
            ListeningPolicySettingQueryService.class
    );

    @Test
    void repositoriesDoNotUseStringBasedQueryAnnotations() {
        for (Class<?> repository : REPOSITORIES) {
            for (Method method : repository.getDeclaredMethods()) {
                assertFalse(
                        method.isAnnotationPresent(Query.class),
                        repository.getSimpleName() + "." + method.getName()
                                + " must not use @Query"
                );
            }
        }
    }

    @Test
    void controllersDependOnlyOnFacades() {
        for (Class<?> controller : CONTROLLERS) {
            for (Field field : controller.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                assertTrue(
                        field.getType().getSimpleName().endsWith("Facade"),
                        controller.getSimpleName() + " must not depend directly on "
                                + field.getType().getSimpleName()
                );
            }
        }
    }

    @Test
    void facadesDoNotOwnTransactions() {
        for (Class<?> facade : FACADES) {
            assertNull(facade.getAnnotation(Transactional.class));
            for (Method method : facade.getDeclaredMethods()) {
                assertNull(method.getAnnotation(Transactional.class));
            }
        }
    }

    @Test
    void queryServicesAreReadOnly() {
        for (Class<?> queryService : QUERY_SERVICES) {
            Transactional transactional = queryService.getAnnotation(
                    Transactional.class
            );
            assertNotNull(
                    transactional,
                    queryService.getSimpleName() + " must declare a transaction"
            );
            assertTrue(
                    transactional.readOnly(),
                    queryService.getSimpleName() + " must be read-only"
            );
        }
    }
}
