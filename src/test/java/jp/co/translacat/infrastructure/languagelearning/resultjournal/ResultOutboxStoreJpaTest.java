package jp.co.translacat.infrastructure.languagelearning.resultjournal;

import jp.co.translacat.domain.languagelearning.resultjournal.model.LearningResultCaptured;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.config.QueryDslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(
        properties = "spring.datasource.url=jdbc:h2:mem:result-journal-tests;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({QueryDslConfig.class, ResultOutboxStoreJpaTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ResultOutboxStoreJpaTest {
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager manager;
    @Autowired
    ResultOutboxStore store;
    @Autowired
    ApplicationEventPublisher publisher;
    @Autowired
    UserRepository users;
    private static final String SOURCE = ResultDeliveryRulesTest.SOURCE;

    @TestConfiguration
    static class Config {
        @Bean
        ResultDeliveryProperties journalProperties() {
            var p = new ResultDeliveryProperties();
            p.setEnabled(true);
            p.setSourceInstanceId(SOURCE);
            return p;
        }

        @Bean
        ResultOutboxStore resultStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
            return new ResultOutboxStore(jdbc, manager, Clock.systemUTC());
        }

        @Bean
        ResultJournalListener journalListener(ResultOutboxStore store, ResultDeliveryProperties p) {
            return new ResultJournalListener(store, p);
        }
    }

    @BeforeEach
    void prepare() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:result-journal-tests"));
        }
        // 이 테스트의 H2 DB만 초기화한다. MySQL/TiDB/LL DB를 대상으로 실행하지 않는다.
        jdbc.execute("DROP TABLE IF EXISTS language_learning_result_outbox");
        jdbc.execute("DROP TABLE IF EXISTS language_learning_result_outbox_stream");
        new ResourceDatabasePopulator(new ClassPathResource("db/result-journal-h2.sql")).execute(jdbc.getDataSource());
    }

    private LearningResultCaptured fact(long userId) {
        return new LearningResultCaptured(userId, "WRITING_SCORED", "123", "{\"resultKind\":\"SCORED_EVALUATION\"}");
    }

    private ResultEnvelope append(long id) {
        return new TransactionTemplate(manager).execute(s -> store.append(SOURCE, fact(id)));
    }

    private long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM language_learning_result_outbox", Long.class);
    }

    private ResultAcknowledgement ack(ResultOutboxStore.Claim claim) {
        var e = claim.event();
        return new ResultAcknowledgement(e.sourceInstanceId(), e.eventId(), e.userId(), e.sequence(), e.payloadSha256(),
                "RECORDED");
    }

    @Test
    void beforeCommitRecordsTheFactInTheSourceTransaction() {
        new TransactionTemplate(manager).executeWithoutResult(s -> {
            publisher.publishEvent(fact(123));
            assertEquals(0L, count());
        });
        assertEquals(1L, count());
    }

    @Test
    void sourceRollbackAlsoRollsBackOutboxAndSequence() {
        String email = UUID.randomUUID() + "@result-journal.test";
        new TransactionTemplate(manager).executeWithoutResult(s -> {
            var user = users.saveAndFlush(User.createLocalUser(email, "test", "test", Role.USER,
                    UUID.randomUUID().toString().substring(0, 20)));
            publisher.publishEvent(fact(user.getId()));
            s.setRollbackOnly();
        });
        assertEquals(0L, count());
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE email=?", Long.class, email));
    }

    @Test
    void outboxFailureRollsBackSourceJpaWriteRatherThanLosingTheEvent() {
        jdbc.execute("DROP TABLE language_learning_result_outbox");
        String email = UUID.randomUUID() + "@result-journal.test";
        assertThrows(RuntimeException.class, () -> new TransactionTemplate(manager).executeWithoutResult(s -> {
            var user = users.saveAndFlush(User.createLocalUser(email, "test", "test", Role.USER,
                    UUID.randomUUID().toString().substring(0, 20)));
            publisher.publishEvent(fact(user.getId()));
        }));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE email=?", Long.class, email));
    }

    @Test
    void appendWithoutSourceTransactionIsRejected() {
        assertThrows(IllegalStateException.class, () -> store.append(SOURCE, fact(123)));
    }

    @Test
    void committedSequencesAreContiguousAfterRollback() {
        append(123);
        new TransactionTemplate(manager).executeWithoutResult(s -> {
            store.append(SOURCE, fact(123));
            s.setRollbackOnly();
        });
        assertEquals(2L, append(123).sequence());
    }

    @Test
    void oneStreamWaitsForAcknowledgementButAnotherUserCanAdvance() {
        append(123);
        append(123);
        append(124);
        var first = store.claim(SOURCE, 60, 10).orElseThrow();
        var secondUser = store.claim(SOURCE, 60, 10).orElseThrow();
        assertNotEquals(first.event().userId(), secondUser.event().userId());
        assertTrue(store.claim(SOURCE, 60, 10).isEmpty());
        store.acknowledge(first, ack(first));
        store.acknowledge(secondUser, ack(secondUser));
        assertEquals(2L, store.claim(SOURCE, 60, 10).orElseThrow().event().sequence());
    }

    @Test
    void expiredLeaseIsReclaimedWithANewTokenAndStaleWorkerCannotAcknowledge() {
        append(123);
        var stale = store.claim(SOURCE, 60, 10).orElseThrow();
        jdbc.update("UPDATE language_learning_result_outbox SET lease_until=?",
                Timestamp.from(Instant.now().minusSeconds(60)));
        var current = store.claim(SOURCE, 60, 10).orElseThrow();
        assertNotEquals(stale.token(), current.token());
        assertEquals(stale.event(), current.event());
        assertFalse(store.acknowledge(stale, ack(stale)));
        assertFalse(store.fail(stale, "REMOTE_UNAVAILABLE", false, 10));
        assertTrue(store.acknowledge(current, ack(current)));
    }

    @Test
    void blockedHeadIsNotSkippedAndResponseMismatchNeverMarksDelivery() {
        append(123);
        append(123);
        var claim = store.claim(SOURCE, 60, 10).orElseThrow();
        var mismatch = new ResultAcknowledgement(SOURCE, claim.event().eventId(), 123, 9, claim.event().payloadSha256(),
                "RECORDED");
        assertThrows(IllegalArgumentException.class, () -> store.acknowledge(claim, mismatch));
        assertTrue(store.fail(claim, "REMOTE_REJECTED", true, 10));
        assertTrue(store.claim(SOURCE, 60, 10).isEmpty());
    }

    @Test
    void repeatedCrashesEventuallyBlockTheHeadWithoutSkippingIt() {
        append(123);
        append(123);
        var first = store.claim(SOURCE, 60, 1).orElseThrow();
        jdbc.update("UPDATE language_learning_result_outbox SET lease_until=? WHERE event_id=?",
                Timestamp.from(Instant.now().minusSeconds(60)), first.event().eventId());
        assertTrue(store.claim(SOURCE, 60, 1).isEmpty());
        assertEquals("ATTEMPTS_EXHAUSTED", jdbc.queryForObject(
                "SELECT last_error_code FROM language_learning_result_outbox WHERE event_id=?", String.class,
                first.event().eventId()));
        assertTrue(store.claim(SOURCE, 60, 1).isEmpty());
    }
}
