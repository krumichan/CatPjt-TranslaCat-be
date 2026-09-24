package jp.co.translacat.domain.languagelearning.listening.outbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.outbox.repository.ListeningOutboxEventRepository;
import jp.co.translacat.domain.languagelearning.setting.port.UserSettingsGateway;
import jp.co.translacat.global.config.QueryDslConfig;
import jp.co.translacat.support.SettingsSnapshotFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:ll-settings-outbox-cutover;" +
                        "MODE=MySQL;" +
                        "DB_CLOSE_DELAY=-1;" +
                        "DB_CLOSE_ON_EXIT=FALSE;" +
                        "NON_KEYWORDS=USER"
        }
)
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
@Import({
        QueryDslConfig.class,
        SettingsSelectionOutboxService.class,
        ListeningOutboxCommandService.class,
        SettingsSelectionOutboxJpaTest.JsonConfiguration.class
})
class SettingsSelectionOutboxJpaTest {

    @Autowired
    SettingsSelectionOutboxService service;

    @Autowired
    ListeningOutboxEventRepository events;

    @MockitoBean
    UserSettingsGateway settings;

    @Test
    void sessionTransactionRollbackAlsoRemovesTheDeliveryEvent() {
        when(settings.getSnapshot(123L))
                .thenReturn(SettingsSnapshotFixtures.user(123));

        long before = events.count();

        service.enqueue(
                123L,
                1001L,
                List.of(ListeningTaskType.SUMMARY)
        );
        events.flush();

        assertTrue(
                events.findByIdempotencyKey(
                        "settings-selection:session:1001"
                ).isPresent()
        );

        TestTransaction.flagForRollback();
        TestTransaction.end();
        TestTransaction.start();

        assertEquals(
                before,
                events.count()
        );

        assertTrue(
                events.findByIdempotencyKey(
                        "settings-selection:session:1001"
                ).isEmpty()
        );
    }

    @Test
    void repeatedSessionCreationKeepsOneOutboxEventAndNeverCallsSettingsPatch() {
        when(settings.getSnapshot(123L))
                .thenReturn(SettingsSnapshotFixtures.user(123));

        service.enqueue(
                123L,
                1002L,
                List.of(ListeningTaskType.SUMMARY)
        );
        events.flush();

        service.enqueue(
                123L,
                1002L,
                List.of(ListeningTaskType.DICTATION)
        );
        events.flush();

        var row = events
                .findByIdempotencyKey(
                        "settings-selection:session:1002"
                )
                .orElseThrow();

        assertTrue(
                row.getPayloadJson().contains("SUMMARY")
        );

        verify(
                settings,
                times(1)
        ).getSnapshot(123L);

        verify(
                settings,
                never()
        ).update(anyLong(), any());
    }

    @Test
    void enqueueRequiresTheSessionTransaction() {
        TestTransaction.end();

        try {
            assertThrows(
                    org.springframework.transaction.IllegalTransactionStateException.class,
                    () -> service.enqueue(
                            123L,
                            1003L,
                            List.of(ListeningTaskType.SUMMARY)
                    )
            );
        } finally {
            TestTransaction.start();
        }
    }

    /**
     * @DataJpaTest는 일반 Component 전체를 스캔하지 않으므로
     * Outbox 직렬화에 필요한 Codec만 테스트 범위에서 명시적으로 제공한다.
     */
    @TestConfiguration
    static class JsonConfiguration {

        @Bean
        @Primary
        LanguageLearningJsonCodec jsonCodec() {
            return new LanguageLearningJsonCodec(
                    new ObjectMapper().findAndRegisterModules()
            );
        }
    }
}