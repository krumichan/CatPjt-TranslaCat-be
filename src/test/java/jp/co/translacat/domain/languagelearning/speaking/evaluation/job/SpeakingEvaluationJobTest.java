package jp.co.translacat.domain.languagelearning.speaking.evaluation.job;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.entity.SpeakingEvaluationJob;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SpeakingEvaluationJobTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 10, 1, 0);
    private SpeakingEvaluationJob job() { return SpeakingEvaluationJob.pending(mock(SpeakingSession.class), 0, "{}", now); }
    @Test void claimsPendingOnceAndDoesNotOverlapAnUnexpiredLease() {
        var job = job();
        String token = job.claim(now, Duration.ofSeconds(10), 2);
        assertThat(token).isNotBlank();
        assertThat(job.claim(now.plusSeconds(9), Duration.ofSeconds(10), 2)).isNull();
        assertThat(job.owns(token)).isTrue();
    }
    @Test void expiredWorkerCannotCommitOrFailARecoveredJob() {
        var job = job();
        String first = job.claim(now, Duration.ofSeconds(10), 2);
        String second = job.claim(now.plusSeconds(10), Duration.ofSeconds(10), 2);
        assertThat(second).isNotEqualTo(first);
        assertThat(job.succeed(first)).isFalse();
        assertThat(job.fail(first, "FAILURE")).isFalse();
        assertThat(job.release(first, now)).isFalse();
        assertThat(job.succeed(second)).isTrue();
        assertThat(job.succeed(second)).isFalse();
    }
    @Test void restartRecoveryIsBoundedAndBecomesRetryableFailure() {
        var job = job();
        job.claim(now, Duration.ofSeconds(10), 2);
        job.claim(now.plusSeconds(10), Duration.ofSeconds(10), 2);
        job.claim(now.plusSeconds(20), Duration.ofSeconds(10), 2);
        assertThat(job.claim(now.plusSeconds(30), Duration.ofSeconds(10), 2)).isNull();
        assertThat(job.getStatus()).isEqualTo(SpeakingEvaluationJob.Status.FAILED);
        assertThat(job.getLastError()).isEqualTo("EVALUATION_RECOVERY_EXHAUSTED");
        job.retry(1, "{\"retry\":1}", now.plusSeconds(31));
        assertThat(job.getManualRetryCount()).isEqualTo(1);
        assertThat(job.getRecoveryCount()).isZero();
        var token = job.claim(now.plusSeconds(31), Duration.ofSeconds(10), 2);
        job.fail(token, "FAILURE");
        assertThatThrownBy(() -> job.retry(1, "{}", now.plusSeconds(32))).isInstanceOf(IllegalStateException.class);
    }
    @Test void executorBackpressureDoesNotSpendRestartRecoveryBudget() {
        var job = job();
        var token = job.claim(now, Duration.ofSeconds(10), 2);
        assertThat(job.release(token, now.plusSeconds(5))).isTrue();
        assertThat(job.claim(now.plusSeconds(4), Duration.ofSeconds(10), 2)).isNull();
        assertThat(job.claim(now.plusSeconds(5), Duration.ofSeconds(10), 2)).isNotNull();
        assertThat(job.getRecoveryCount()).isZero();
    }
    @Test void successfulJobCannotBeManuallyRetried() {
        var job = job();
        job.succeed(job.claim(now, Duration.ofSeconds(10), 2));
        assertThatThrownBy(() -> job.retry(1, "{}", now)).isInstanceOf(IllegalStateException.class);
    }
}
