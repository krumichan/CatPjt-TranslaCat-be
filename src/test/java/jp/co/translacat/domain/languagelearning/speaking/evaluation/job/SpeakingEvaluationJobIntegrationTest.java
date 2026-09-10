package jp.co.translacat.domain.languagelearning.speaking.evaluation.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.activity.entity.LearningActivity;
import jp.co.translacat.domain.languagelearning.activity.repository.*;
import jp.co.translacat.domain.languagelearning.common.enums.*;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.profile.service.SpeakingProfileSignalService;
import jp.co.translacat.domain.languagelearning.profile.repository.LearningProfileEvidenceRepository;
import jp.co.translacat.domain.languagelearning.setting.service.*;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.port.SpeakingAiClient;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.*;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.factory.SpeakingEvaluationRequestFactory;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.entity.SpeakingEvaluationJob;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.listener.SpeakingEvaluationJobEventListener;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.model.*;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.repository.SpeakingEvaluationJobRepository;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.service.*;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.policy.SpeakingEvaluationEligibilityPolicy;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.entity.SpeakingReadAloudProblemEvaluation;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.repository.SpeakingReadAloudProblemEvaluationRepository;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.service.SpeakingReadAloudProblemEvaluationService;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.repository.*;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.service.*;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.validator.SpeakingEvaluationResponseValidator;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.repository.SpeakingSessionRepository;
import jp.co.translacat.domain.languagelearning.speaking.session.service.*;
import jp.co.translacat.domain.languagelearning.speaking.usage.service.SpeakingAiUsageCommandService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.config.QueryDslConfig;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static jp.co.translacat.domain.languagelearning.speaking.evaluation.release.SpeakingReleaseFixtures.*;

/** Real H2, repositories, Spring transaction proxies and AFTER_COMMIT events; AI/provider is mocked. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:speaking-release-jobs;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=USER"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({QueryDslConfig.class, SpeakingEvaluationJobIntegrationTest.JsonConfiguration.class,
        LanguageLearningJsonCodec.class, SpeakingEvaluationJobCommandService.class,
        SpeakingEvaluationJobQueryService.class, SpeakingEvaluationJobQueueService.class,
        SpeakingEvaluationJobEventListener.class, SpeakingEvaluationJobWorker.class,
        SpeakingEvaluationResultCommandService.class, SpeakingEvaluationResponseValidator.class,
        SpeakingEvaluationEligibilityPolicy.class, SpeakingAiUsageCommandService.class,
        SpeakingSessionQueryService.class, SpeakingEvaluationRetryCommandService.class,
        SpeakingReadAloudProblemEvaluationService.class, SpeakingSessionLifecycleService.class})
class SpeakingEvaluationJobIntegrationTest {
    @TestConfiguration static class JsonConfiguration {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
    }
    @Autowired PlatformTransactionManager transactions;
    @Autowired UserRepository users;
    @Autowired SpeakingSessionRepository sessions;
    @Autowired LearningActivityRepository activities;
    @Autowired SpeakingEvaluationJobRepository jobs;
    @Autowired SpeakingEvaluationRepository evaluations;
    @Autowired SpeakingEvaluationMetricRepository metrics;
    @Autowired EvaluationMetricHistoryRepository history;
    @Autowired LearningProfileEvidenceRepository evidence;
    @Autowired SpeakingReadAloudProblemEvaluationRepository problems;
    @Autowired SpeakingEvaluationJobCommandService command;
    @Autowired SpeakingEvaluationJobWorker worker;
    @Autowired SpeakingEvaluationJobQueueService queue;
    @Autowired SpeakingEvaluationJobQueryService query;
    @Autowired SpeakingEvaluationRetryCommandService retry;
    @Autowired SpeakingReadAloudProblemEvaluationService readAloud;
    @Autowired LanguageLearningJsonCodec codec;
    @MockitoBean SpeakingAiClient aiClient;
    @MockitoBean SpeakingEvaluationJobDispatcher dispatcher;
    @MockitoBean SpeakingEvaluationRequestFactory requestFactory;
    @MockitoBean SpeakingSessionPolicySnapshotService snapshots;
    @MockitoBean SpeakingProfileSignalService profile;
    @MockitoBean SpeakingSessionUsageQueryService usageQuery;
    @MockitoBean LanguageLearningAdminSettingQueryService adminSettings;
    @MockitoBean LanguageLearningUserSettingQueryService userSettings;
    @MockitoBean SpeakingSessionCompletionCommandService completion;
    private TransactionTemplate tx;

    @BeforeEach void setup() { tx = new TransactionTemplate(transactions); }

    private record Seed(long userId, SpeakingEvaluationJobKey key, long activityId) { }
    private Seed seed(String fixture, int problemIndex) {
        return tx.execute(status -> {
            String uid = UUID.randomUUID().toString().replace("-", "");
            User user = users.save(User.createLocalUser(uid + "@release.test", "pw", "release", Role.USER, uid.substring(0, 20)));
            SpeakingSession session = session(user);
            if (problemIndex > 0) ReflectionTestUtils.setField(session, "practiceMode", SpeakingPracticeMode.READ_ALOUD);
            session.complete(true);
            sessions.saveAndFlush(session);
            var activity = activities.saveAndFlush(LearningActivity.create(user, LearningSource.SPEAKING,
                    session.getId().toString(), LocalDate.now(), "Release test", 60, LocalDateTime.now(),
                    LocalDateTime.now(), LearningActivityStatus.EVALUATING));
            if (problemIndex > 0) problems.saveAndFlush(SpeakingReadAloudProblemEvaluation.pending(session, problemIndex, 2));
            var job = jobs.saveAndFlush(SpeakingEvaluationJob.pending(session, problemIndex,
                    codec.write(request(fixture)), LocalDateTime.now().minusSeconds(1)));
            return new Seed(user.getId(), new SpeakingEvaluationJobKey(job.getId(), session.getId()), activity.getId());
        });
    }
    private SpeakingEvaluationStatus sessionStatus(Seed seed) {
        return tx.execute(status -> sessions.findById(seed.key().sessionId()).orElseThrow().getEvaluationStatus());
    }
    private SpeakingEvaluationJob.Status jobStatus(Seed seed) {
        return tx.execute(status -> jobs.findById(seed.key().jobId()).orElseThrow().getStatus());
    }
    private void expire(Seed seed) {
        tx.executeWithoutResult(status -> ReflectionTestUtils.setField(jobs.findById(seed.key().jobId()).orElseThrow(),
                "availableAt", LocalDateTime.now().minusMinutes(1)));
    }

    @Test void aiRunsOutsideTransactionAndFailurePersistsInANewTransaction() {
        var seed = seed("evaluated", 0);
        var claim = command.claim(seed.key()).orElseThrow();
        when(aiClient.evaluate(claim.request())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            throw new IllegalStateException("simulated timeout");
        });
        worker.execute(claim);
        assertThat(sessionStatus(seed)).isEqualTo(SpeakingEvaluationStatus.FAILED);
        assertThat(jobStatus(seed)).isEqualTo(SpeakingEvaluationJob.Status.FAILED);
        assertThat(activities.findById(seed.activityId()).orElseThrow().getStatus())
                .isEqualTo(LearningActivityStatus.EVALUATION_FAILED);
        assertThat(evaluations.findFirstBySessionIdOrderByEvaluatedAtDesc(seed.key().sessionId())).isEmpty();
    }

    @Test void resultMetricAndProfileFailureRollBackBeforeFailureStateIsSaved() {
        var seed = seed("evaluated", 0);
        var claim = command.claim(seed.key()).orElseThrow();
        long metricsBefore = metrics.count();
        long historyBefore = history.count();
        when(aiClient.evaluate(claim.request())).thenReturn(response("evaluated"));
        doThrow(new IllegalStateException("profile write failed")).when(profile).apply(eq(seed.userId()), anyList(), anyDouble());
        worker.execute(claim);
        assertThat(evaluations.findFirstBySessionIdOrderByEvaluatedAtDesc(seed.key().sessionId())).isEmpty();
        assertThat(metrics.count()).isEqualTo(metricsBefore);
        assertThat(history.count()).isEqualTo(historyBefore);
        assertThat(sessionStatus(seed)).isEqualTo(SpeakingEvaluationStatus.FAILED);
        assertThat(jobStatus(seed)).isEqualTo(SpeakingEvaluationJob.Status.FAILED);
    }

    @Test void identicalDuplicateCompletionDoesNotWriteMetricsOrProfileTwice() {
        var seed = seed("evaluated", 0);
        var claim = command.claim(seed.key()).orElseThrow();
        assertThat(command.complete(claim, response("evaluated"))).isTrue();
        assertThat(command.complete(claim, response("evaluated"))).isFalse();
        var evaluation = evaluations.findFirstBySessionIdOrderByEvaluatedAtDesc(seed.key().sessionId()).orElseThrow();
        assertThat(evaluation.getEvaluationVersion()).isEqualTo("speaking-evaluation");
        assertThat(metrics.findAllByEvaluationIdOrderByMetricTypeAsc(evaluation.getId())).hasSize(8);
        verify(profile, times(1)).apply(eq(seed.userId()), anyList(), anyDouble());
        assertThat(sessionStatus(seed)).isEqualTo(SpeakingEvaluationStatus.EVALUATED);
    }

    @Test void precheckInsufficientEvidenceIsTerminalWithoutMetricOrProfileWrites() {
        var seed = seed("precheck-insufficient", 0);
        var claim = command.claim(seed.key()).orElseThrow();
        assertThat(command.complete(claim, response("precheck-insufficient"))).isTrue();
        var evaluation = evaluations.findFirstBySessionIdOrderByEvaluatedAtDesc(seed.key().sessionId()).orElseThrow();
        assertThat(evaluation.getOverallScore()).isNull();
        assertThat(metrics.findAllByEvaluationIdOrderByMetricTypeAsc(evaluation.getId())).isEmpty();
        verifyNoInteractions(profile);
        assertThat(sessionStatus(seed)).isEqualTo(SpeakingEvaluationStatus.INSUFFICIENT_EVIDENCE);
        assertThat(jobStatus(seed)).isEqualTo(SpeakingEvaluationJob.Status.SUCCEEDED);
    }

    @Test void retryCommitsPendingAndAnAfterCommitEventAndDuplicateClickDoesNotConsumeAnotherRetry() {
        var seed = seed("evaluated", 0);
        command.fail(command.claim(seed.key()).orElseThrow());
        when(snapshots.read(any())).thenReturn(policy(true));
        retry.retry(seed.userId(), seed.key().sessionId());
        verify(dispatcher).dispatch(seed.key());
        assertThat(sessionStatus(seed)).isEqualTo(SpeakingEvaluationStatus.PENDING);
        assertThat(jobStatus(seed)).isEqualTo(SpeakingEvaluationJob.Status.PENDING);
        retry.retry(seed.userId(), seed.key().sessionId());
        verify(dispatcher, times(1)).dispatch(seed.key());
        var next = command.claim(seed.key()).orElseThrow();
        assertThat(next.manualRetryAttempt()).isEqualTo(1);
        assertThat(next.request().userTurns()).isEqualTo(request("evaluated").userTurns());
        command.fail(next);
        assertThatThrownBy(() -> retry.retry(seed.userId(), seed.key().sessionId())).isInstanceOf(BusinessException.class);
    }

    @Test void rolledBackRetryDoesNotDispatchOrConsumeRetryCount() {
        var seed = seed("evaluated", 0);
        command.fail(command.claim(seed.key()).orElseThrow());
        when(snapshots.read(any())).thenReturn(policy(true));
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            retry.retry(seed.userId(), seed.key().sessionId());
            verifyNoInteractions(dispatcher);
            throw new IllegalStateException("abort outer transaction");
        })).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(dispatcher);
        assertThat(jobStatus(seed)).isEqualTo(SpeakingEvaluationJob.Status.FAILED);
        assertThat(jobs.findById(seed.key().jobId()).orElseThrow().getManualRetryCount()).isZero();
    }

    @Test void unfinishedDurableJobIsDiscoverableAndStaleWorkerCannotOverwriteRecovery() {
        var seed = seed("evaluated", 0);
        assertThat(query.findDue(100)).contains(seed.key());
        var first = command.claim(seed.key()).orElseThrow();
        assertThat(query.findDue(100)).doesNotContain(seed.key());
        expire(seed);
        assertThat(query.findDue(100)).contains(seed.key());
        var second = command.claim(seed.key()).orElseThrow();
        assertThat(command.complete(first, response("evaluated"))).isFalse();
        command.fail(first);
        assertThat(jobStatus(seed)).isEqualTo(SpeakingEvaluationJob.Status.RUNNING);
        assertThat(command.complete(second, response("evaluated"))).isTrue();
        command.fail(first);
        assertThat(sessionStatus(seed)).isEqualTo(SpeakingEvaluationStatus.EVALUATED);
    }

    @Test void concurrentClaimersObtainOnlyOneLiveLease() throws Exception {
        var seed = seed("evaluated", 0);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task = () -> { ready.countDown(); if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("claim barrier timed out"); return command.claim(seed.key()).isPresent(); };
            var first = pool.submit(task);
            var second = pool.submit(task);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat((first.get(10, TimeUnit.SECONDS) ? 1 : 0) + (second.get(10, TimeUnit.SECONDS) ? 1 : 0)).isEqualTo(1);
        }
    }

    @Test void failedSubmittedProblemCanRetryAfterCompletionWithoutReopeningRecording() {
        var seed = seed("precheck-insufficient", 5);
        command.fail(command.claim(seed.key()).orElseThrow());
        when(snapshots.read(any())).thenReturn(policy(true));
        var accepted = readAloud.retry(seed.userId(), seed.key().sessionId(), 5);
        assertThat(accepted.status()).isEqualTo("PENDING");
        assertThat(accepted.manualRetryCount()).isEqualTo(1);
        assertThat(sessions.findById(seed.key().sessionId()).orElseThrow().isActive()).isFalse();
        verify(dispatcher).dispatch(seed.key());
        // Replaying the original final submit returns the submitted evaluation, not a new job.
        assertThat(readAloud.submit(seed.userId(), seed.key().sessionId(), 5).status()).isEqualTo("PENDING");
        verify(dispatcher, times(1)).dispatch(seed.key());
    }

    @Test void anotherUserCannotRetryTheSubmittedProblem() {
        var seed = seed("precheck-insufficient", 5);
        command.fail(command.claim(seed.key()).orElseThrow());
        assertThatThrownBy(() -> readAloud.retry(seed.userId() + 100000, seed.key().sessionId(), 5))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(dispatcher);
        assertThat(jobStatus(seed)).isEqualTo(SpeakingEvaluationJob.Status.FAILED);
    }

    @Test void disabledSessionDoesNotEnqueueAiWork() {
        var seed = seed("evaluated", 0);
        when(snapshots.read(any())).thenReturn(policy(false));
        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                queue.enqueue(sessions.findOneById(seed.key().sessionId()).orElseThrow(), 1)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(dispatcher, requestFactory, aiClient);
    }
    @Test void concurrentSessionsDoNotLoseProfileContributionsForTheSameUser() throws Exception {
        var seed = seed("evaluated", 0);
        var realProfile = new SpeakingProfileSignalService(evidence, users);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Void> task = () -> {
                ready.countDown();
                if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("profile barrier timed out");
                new TransactionTemplate(transactions).executeWithoutResult(status ->
                        realProfile.apply(seed.userId(), response("evaluated").profileSignals(), 0.9));
                return null;
            };
            var first = pool.submit(task);
            var second = pool.submit(task);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        var signal = response("evaluated").profileSignals().getFirst();
        var saved = evidence.findByUserIdAndSourceAndPatternKeyAndDirection(seed.userId(), LearningSource.SPEAKING,
                signal.patternKey(), signal.direction()).orElseThrow();
        assertThat(saved.getEvidenceCount()).isEqualTo(2);
    }

}
