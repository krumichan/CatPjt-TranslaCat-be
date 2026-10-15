package jp.co.translacat.domain.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManagerFactory;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.voice.config.VoicePolicyProperties;
import jp.co.translacat.domain.voice.entity.*;
import jp.co.translacat.domain.voice.enums.*;
import jp.co.translacat.domain.voice.model.VoiceStreamContext;
import jp.co.translacat.domain.voice.repository.*;
import jp.co.translacat.domain.voice.service.*;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.japanese.SudachiFuriganaProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class VoiceFinalizationConcurrencyTest {
    private AnnotationConfigApplicationContext beans;
    private TransactionTemplate transactions;
    private VoiceSessionCommandService commands;
    private VoiceFinalResultCommandService finals;
    private VoiceSessionRepository sessions;
    private VoiceSegmentRepository segments;
    private VoiceUsageLedgerRepository usage;
    private VoiceStreamContext stream;
    private ObjectMapper mapper;

    @BeforeEach
    void prepare() {
        // 준비: Voice/User 엔티티만 사용하는 별도 H2 DB와 실제 Spring 트랜잭션 프록시를 구성한다.
        beans = new AnnotationConfigApplicationContext(DatabaseConfig.class);
        transactions = new TransactionTemplate(beans.getBean(PlatformTransactionManager.class));
        commands = beans.getBean(VoiceSessionCommandService.class);
        finals = beans.getBean(VoiceFinalResultCommandService.class);
        sessions = beans.getBean(VoiceSessionRepository.class);
        segments = beans.getBean(VoiceSegmentRepository.class);
        usage = beans.getBean(VoiceUsageLedgerRepository.class);
        mapper = beans.getBean(ObjectMapper.class);
        var entityManager = SharedEntityManagerCreator.createSharedEntityManager(
                beans.getBean(EntityManagerFactory.class)
        );

        stream = transactions.execute(status -> {
            User user = User.createLocalUser("voice-test@example.invalid", "unused", "Voice test",
                    Role.USER, "voice-test");
            entityManager.persist(user);
            VoiceSession session = VoiceSession.create(user, VoiceMode.MEDIA, VoiceSourceLanguageMode.AUTO,
                    "ko", false, "{}");
            sessions.saveAndFlush(session);
            return new VoiceStreamContext(user.getId(), session.getId(), VoiceChannel.REMOTE, "test-connection",
                    VoiceMode.MEDIA, VoiceSourceLanguageMode.AUTO, null, null, "ko", "{}", 0);
        });
    }

    @AfterEach
    void close() {
        if (beans != null) {
            beans.close();
        }
    }

    @Test
    void failureWaitsForInFlightFinalThenDeletesItsTranscript() throws Exception {
        // 준비: Final 저장 트랜잭션이 세션 잠금을 잡은 뒤 commit 직전에 멈춘다.
        var finalWritten = new CountDownLatch(1);
        var releaseFinal = new CountDownLatch(1);
        var failureStarted = new CountDownLatch(1);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var writer = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(status -> {
                finals.persistTranscriptFinal(stream, transcript("final-1"), 1);
                finalWritten.countDown();
                await(releaseFinal);
            }), workers);

            try {
                assertThat(finalWritten.await(5, TimeUnit.SECONDS)).isTrue();

                // 실행: timeout 실패 정리는 같은 row lock이 풀릴 때까지 기다려야 한다.
                var failure = CompletableFuture.runAsync(() -> {
                    failureStarted.countDown();
                    commands.fail(stream.userId(), stream.sessionId());
                }, workers);
                assertThat(failureStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> failure.get(150, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                releaseFinal.countDown();
                writer.get(5, TimeUnit.SECONDS);
                failure.get(5, TimeUnit.SECONDS);

                // 검증: 먼저 쓰인 원문은 삭제되고 내용 없는 사용량 ledger만 남는다.
                assertThat(sessions.findById(stream.sessionId()).orElseThrow().getStatus())
                        .isEqualTo(VoiceSessionStatus.FAILED);
                assertThat(segments.count()).isZero();
                assertThat(usage.count()).isEqualTo(1);
            } finally {
                releaseFinal.countDown();
            }
        }
    }

    @Test
    void lateCompletedWaitsForFailureAndCannotRecreateDeletedTranscript() throws Exception {
        // 준비: 원문 저장 후 실패/미보관 삭제 트랜잭션을 commit 직전에 멈춘다.
        finals.persistTranscriptFinal(stream, transcript("final-1"), 1);
        var failureApplied = new CountDownLatch(1);
        var releaseFailure = new CountDownLatch(1);
        var completionStarted = new CountDownLatch(1);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var failure = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(status -> {
                commands.fail(stream.userId(), stream.sessionId());
                failureApplied.countDown();
                await(releaseFailure);
            }), workers);

            try {
                assertThat(failureApplied.await(5, TimeUnit.SECONDS)).isTrue();

                // 실행: 삭제가 진행 중인 시점의 늦은 Completed는 세션 잠금부터 기다린다.
                var completion = CompletableFuture.runAsync(() -> {
                    completionStarted.countDown();
                    finals.persistCompleted(stream, completed("completed-1"), 1, 0);
                }, workers);
                assertThat(completionStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> completion.get(150, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                releaseFailure.countDown();
                failure.get(5, TimeUnit.SECONDS);

                // 검증: terminal 상태가 확인되면 저장을 거절하고 삭제된 원문/번역은 복원하지 않는다.
                assertThatThrownBy(() -> completion.get(5, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(BusinessException.class)
                        .satisfies(error -> assertThat(((BusinessException) error.getCause()).getErrorCode())
                                .isEqualTo(VoiceErrorCode.LATE_FINAL_EVENT));
                assertThat(segments.count()).isZero();
                assertThat(usage.count()).isEqualTo(1);
            } finally {
                releaseFailure.countDown();
            }
        }
    }

    @Test
    void disconnectedCallbackKeepsErrorAndLaterCompletionDeletesUnretainedTranscript() {
        // 준비: 마지막 원문을 저장한 뒤 ACK 없이 끊긴 채널을 DB에 남긴다.
        finals.persistTranscriptFinal(stream, transcript("final-1"), 1);
        storeFailedChannel(false);

        // 실행: 소켓 registry가 없어도 DB의 ERROR를 완료 판정에 사용한다.
        commands.startCompleting(stream.userId(), stream.sessionId());
        boolean completed = commands.finishCompletion(stream.userId(), stream.sessionId());

        // 검증: 실패 정리는 commit되고 미보관 원문이 남지 않는다.
        assertThat(completed).isFalse();
        assertThat(sessions.findById(stream.sessionId()).orElseThrow().getStatus())
                .isEqualTo(VoiceSessionStatus.FAILED);
        assertThat(segments.count()).isZero();
    }

    @Test
    void successfullyReconnectedChannelDoesNotKeepAnObsoleteFailure() {
        // 준비: 오류가 났지만 새 연결에서 정상 처리를 마치고 disconnect한 채널이다.
        storeFailedChannel(true);

        // 실행
        commands.startCompleting(stream.userId(), stream.sessionId());
        boolean completed = commands.finishCompletion(stream.userId(), stream.sessionId());

        // 검증: 복구된 과거 오류는 현재 세션 완료를 막지 않는다.
        assertThat(completed).isTrue();
        assertThat(sessions.findById(stream.sessionId()).orElseThrow().getStatus())
                .isEqualTo(VoiceSessionStatus.COMPLETED);
    }

    @Test
    void completingRequestRefreshesCachedSessionAfterConcurrentFinalCommit() throws Exception {
        // 준비: OSIV처럼 HTTP 요청 하나가 두 트랜잭션 사이에도 같은 EntityManager를 유지한다.
        var factory = beans.getBean(EntityManagerFactory.class);
        var requestEntityManager = factory.createEntityManager();
        TransactionSynchronizationManager.bindResource(factory, new EntityManagerHolder(requestEntityManager));
        try {
            commands.startCompleting(stream.userId(), stream.sessionId());
            long cachedVersion = requestEntityManager.find(VoiceSession.class, stream.sessionId()).getVersion();

            // 실행: HTTP flush 대기 중 AI worker의 별도 EntityManager가 사용량/version을 commit한다.
            try (var worker = Executors.newSingleThreadExecutor()) {
                CompletableFuture.runAsync(() -> finals.persistTranscriptFinal(stream, transcript("concurrent-final"), 1),
                        worker).get(5, TimeUnit.SECONDS);
            }
            assertThat(requestEntityManager.find(VoiceSession.class, stream.sessionId()).getVersion())
                    .isEqualTo(cachedVersion);
            assertThat(commands.finishCompletion(stream.userId(), stream.sessionId())).isTrue();
        } finally {
            TransactionSynchronizationManager.unbindResource(factory);
            requestEntityManager.close();
        }

        // 검증: 마지막 사용량을 잃거나 optimistic-lock 오류 없이 종료하고 미보관 원문만 삭제한다.
        var completedSession = sessions.findById(stream.sessionId()).orElseThrow();
        assertThat(completedSession.getStatus()).isEqualTo(VoiceSessionStatus.COMPLETED);
        assertThat(completedSession.getProcessedAudioMs()).isEqualTo(900);
        assertThat(segments.count()).isZero();
        assertThat(usage.count()).isEqualTo(1);
    }

    @Test
    void failedDrainRefreshesCachedUsageBeforeDeletingUnretainedTranscript() throws Exception {
        // 준비: 완료 대기 실패도 요청에 남아 있는 이전 version으로 저장하지 않아야 한다.
        var factory = beans.getBean(EntityManagerFactory.class);
        var requestEntityManager = factory.createEntityManager();
        TransactionSynchronizationManager.bindResource(factory, new EntityManagerHolder(requestEntityManager));
        try {
            commands.startCompleting(stream.userId(), stream.sessionId());

            // 실행: 별도 AI worker commit 직후 같은 HTTP 요청의 실패 정리를 수행한다.
            try (var worker = Executors.newSingleThreadExecutor()) {
                CompletableFuture.runAsync(() -> finals.persistTranscriptFinal(stream, transcript("before-failure"), 1),
                        worker).get(5, TimeUnit.SECONDS);
            }
            commands.fail(stream.userId(), stream.sessionId());
        } finally {
            TransactionSynchronizationManager.unbindResource(factory);
            requestEntityManager.close();
        }

        // 검증: 사용량을 보존하면서 실패 상태/미보관 삭제가 commit된다.
        var failed = sessions.findById(stream.sessionId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(VoiceSessionStatus.FAILED);
        assertThat(failed.getProcessedAudioMs()).isEqualTo(900);
        assertThat(segments.count()).isZero();
        assertThat(usage.count()).isEqualTo(1);
    }

    @Test
    void nestedLanguageUpdatePreservesFinalUsageInTheSameTransaction() {
        // 준비: Final 저장 중 언어 lock 갱신이 같은 세션을 다시 조회한다.
        transactions.executeWithoutResult(status -> {
            var channel = VoiceSessionChannel.create(sessions.findById(stream.sessionId()).orElseThrow(),
                    VoiceChannel.REMOTE, null);
            channel.connecting(stream.connectionId());
            channel.streaming(stream.connectionId());
            beans.getBean(VoiceSessionChannelRepository.class).saveAndFlush(channel);
        });
        var event = transcript("language-lock");
        event.put("lockedLanguage", "en");

        // 실행
        finals.persistTranscriptFinal(stream, event, 1);

        // 검증: 완료 전용 refresh가 nested worker 경로의 미commit 사용량을 덮지 않는다.
        assertThat(sessions.findById(stream.sessionId()).orElseThrow().getProcessedAudioMs()).isEqualTo(900);
        assertThat(usage.findAll().getFirst().getProcessedAudioMs()).isEqualTo(900);
        assertThat(beans.getBean(VoiceSessionChannelRepository.class).findAll().getFirst().getLastLockedLanguage())
                .isEqualTo("en");
    }

    @Test
    void completionMakesOrphanedTranslationsRetryableAndKeepsSource() {
        // 준비: 재접속 전 번역을 받지 못한 원문이 보관 세션에 남는다.
        var retained = createRetainedSession();
        finals.persistTranscriptFinal(retained, transcript("orphaned-final"), 1);

        // 실행
        commands.startCompleting(retained.userId(), retained.sessionId());
        assertThat(commands.finishCompletion(retained.userId(), retained.sessionId())).isTrue();

        // 검증: 종료된 세션에 영구 TRANSLATING이 남지 않고 원문 기반 재시도 상태가 된다.
        var segment = segments.findAll().getFirst();
        assertThat(segment.getSourceText()).isEqualTo("The meeting is over.");
        assertThat(segment.getStatus()).isEqualTo(VoiceSegmentStatus.TRANSLATION_FAILED);
        assertThat(segment.getErrorCode()).isEqualTo(VoiceErrorCode.AI_CONNECTION_CLOSED);
    }

    @Test
    void repeatedCompletionRepairsPreviouslyCompletedSessionWithOrphanedTranslations() {
        // 준비: 이전 버전이 완료 상태만 저장하고 미완료 문장을 남긴 데이터를 구성한다.
        var retained = createRetainedSession();
        finals.persistTranscriptFinal(retained, transcript("legacy-orphan"), 1);
        transactions.executeWithoutResult(status -> {
            var session = sessions.findOwnedForUpdate(retained.sessionId(), retained.userId()).orElseThrow();
            session.startCompleting();
            session.complete();
            var oldChannel = VoiceSessionChannel.create(session, VoiceChannel.REMOTE, null);
            oldChannel.connecting("old");
            oldChannel.error("old");
            beans.getBean(VoiceSessionChannelRepository.class).save(oldChannel);
        });

        // 실행: 기존 완료 시간/사용량은 유지하면서 terminal 정리만 다시 적용한다.
        var completedAt = sessions.findById(retained.sessionId()).orElseThrow().getCompletedAt();
        assertThat(commands.startCompleting(retained.userId(), retained.sessionId())).isFalse();
        assertThat(commands.finishCompletion(retained.userId(), retained.sessionId())).isTrue();

        // 검증
        assertThat(segments.findAll().getFirst().getStatus()).isEqualTo(VoiceSegmentStatus.TRANSLATION_FAILED);
        assertThat(sessions.findById(retained.sessionId()).orElseThrow().getCompletedAt()).isEqualTo(completedAt);
        assertThat(sessions.findById(retained.sessionId()).orElseThrow().getStatus()).isEqualTo(VoiceSessionStatus.COMPLETED);
        assertThat(usage.count()).isEqualTo(1);
    }

    @Test
    void repeatedFailedCompletionPreservesFailureTimestampAndRetainedSource() {
        // 준비: 종료 실패 전에 확정 원문을 저장한 보관 세션이다.
        var retained = createRetainedSession();
        finals.persistTranscriptFinal(retained, transcript("before-failed-complete"), 1);
        commands.startCompleting(retained.userId(), retained.sessionId());
        commands.fail(retained.userId(), retained.sessionId());
        var failedAt = sessions.findById(retained.sessionId()).orElseThrow().getCompletedAt();

        // 실행: 재시도와 unmount 정리가 반복되어도 실패를 재활성화하지 않는다.
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThat(commands.startCompleting(retained.userId(), retained.sessionId())).isFalse();
            assertThat(commands.finishCompletion(retained.userId(), retained.sessionId())).isFalse();
        }

        // 검증: 실패 상태/시각/원문과 사용량을 보존하고 번역 성공으로 바꾸지 않는다.
        var failed = sessions.findById(retained.sessionId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(VoiceSessionStatus.FAILED);
        assertThat(failed.getCompletedAt()).isEqualTo(failedAt);
        var segment = segments.findAll().getFirst();
        assertThat(segment.getSourceText()).isEqualTo("The meeting is over.");
        assertThat(segment.getStatus()).isEqualTo(VoiceSegmentStatus.TRANSLATION_FAILED);
        assertThat(segment.getErrorCode()).isEqualTo(VoiceErrorCode.COMPLETE_TIMEOUT);
        assertThat(usage.count()).isEqualTo(1);
    }

    @Test
    void concurrentCleanupWaitsForFailureAndCannotTurnItIntoCompleted() throws Exception {
        // 준비: 첫 종료 요청이 시작된 뒤 실패 트랜잭션이 row lock을 보유한다.
        finals.persistTranscriptFinal(stream, transcript("before-concurrent-close"), 1);
        assertThat(commands.startCompleting(stream.userId(), stream.sessionId())).isTrue();
        var failureApplied = new CountDownLatch(1);
        var releaseFailure = new CountDownLatch(1);
        var cleanupStarted = new CountDownLatch(1);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var failure = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(status -> {
                commands.fail(stream.userId(), stream.sessionId());
                failureApplied.countDown();
                await(releaseFailure);
            }), workers);

            try {
                assertThat(failureApplied.await(5, TimeUnit.SECONDS)).isTrue();

                // 실행: 동시에 도착한 정리 요청은 실패 commit을 기다린 뒤 같은 terminal 상태를 읽는다.
                var cleanup = CompletableFuture.supplyAsync(() -> {
                    cleanupStarted.countDown();
                    return commands.startCompleting(stream.userId(), stream.sessionId());
                }, workers);
                assertThat(cleanupStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> cleanup.get(150, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                releaseFailure.countDown();
                failure.get(5, TimeUnit.SECONDS);

                // 검증: 반복 시작과 먼저 시작했던 종료의 후처리 모두 실패를 보존한다.
                assertThat(cleanup.get(5, TimeUnit.SECONDS)).isFalse();
                assertThat(commands.finishCompletion(stream.userId(), stream.sessionId())).isFalse();
                assertThat(sessions.findById(stream.sessionId()).orElseThrow().getStatus())
                        .isEqualTo(VoiceSessionStatus.FAILED);
                assertThat(segments.count()).isZero();
                assertThat(usage.count()).isEqualTo(1);
            } finally {
                releaseFailure.countDown();
            }
        }
    }

    private VoiceStreamContext createRetainedSession() {
        return transactions.execute(status -> {
            var owner = sessions.findById(stream.sessionId()).orElseThrow().getUser();
            var session = VoiceSession.create(owner, VoiceMode.MEDIA, VoiceSourceLanguageMode.AUTO,
                    "ko", true, "{}");
            sessions.saveAndFlush(session);
            return new VoiceStreamContext(stream.userId(), session.getId(), VoiceChannel.REMOTE, "retained",
                    VoiceMode.MEDIA, VoiceSourceLanguageMode.AUTO, null, null, "ko", "{}", 0);
        });
    }

    private void storeFailedChannel(boolean reconnect) {
        transactions.executeWithoutResult(status -> {
            var session = sessions.findById(stream.sessionId()).orElseThrow();
            var channel = VoiceSessionChannel.create(session, VoiceChannel.REMOTE, null);
            channel.connecting("old");
            channel.streaming("old");
            channel.error("old");
            channel.disconnected("old");
            if (reconnect) {
                channel.connecting("new");
                channel.streaming("new");
                channel.disconnected("new");
            }
            beans.getBean(VoiceSessionChannelRepository.class).saveAndFlush(channel);
        });
    }

    private ObjectNode transcript(String eventId) {
        var event = mapper.createObjectNode();
        event.put("eventId", eventId);
        event.put("sourceText", "The meeting is over.");
        event.put("startedAtOffsetMs", 0);
        event.put("endedAtOffsetMs", 1200);
        event.put("speechDurationMs", 900);
        event.put("detectedLanguage", "en");
        return event;
    }

    private ObjectNode completed(String eventId) {
        var event = transcript(eventId);
        event.put("targetLanguage", "ko");
        event.put("translatedText", "회의가 끝났습니다.");
        return event;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for the test transaction barrier");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = VoiceSessionRepository.class)
    @Import({VoiceSessionCommandService.class, VoiceFinalResultCommandService.class, VoiceStreamCommandService.class})
    static class DatabaseConfig {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:voice-" + UUID.randomUUID()
                    + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setManagedTypes(PersistenceManagedTypes.of(User.class.getName(), VoiceSession.class.getName(),
                    VoiceSessionChannel.class.getName(), VoiceSegment.class.getName(), VoiceUsageLedger.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.show_sql", "false",
                    "hibernate.physical_naming_strategy",
                    "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean
        JPAQueryFactory queryFactory(EntityManagerFactory factory) {
            return new JPAQueryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory));
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        VoiceSourceReadingService voiceSourceReadingService(ObjectMapper objectMapper) {
            // 준비: 트랜잭션 경계 테스트에서는 사전 파일을 열지 않고 실제 읽기 폴백 정책을 사용한다.
            return new VoiceSourceReadingService(mock(SudachiFuriganaProcessor.class), objectMapper);
        }

        @Bean
        VoicePolicyProperties policy() {
            return new VoicePolicyProperties(3600000, 7200000, 6400, 3000, 100, 30,
                    3000, 10, 3, 25000, 90, 300, 250, 10000, 0.8, 0.85, 3);
        }
    }
}
