package jp.co.translacat.domain.languagelearning.listening.session;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningSessionStatus;
import jp.co.translacat.domain.languagelearning.listening.session.entity.ListeningSession;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ListeningSessionEvaluationLifecycleTest {

    @Test
    void sessionCanReleaseActiveSlotWhileEvaluationContinues() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 9, 6, 16, 0);
        ListeningSession session = ListeningSession.create(
                null,
                null,
                "[]",
                "{}",
                "{}",
                "listening-e2e",
                startedAt
        );

        assertThat(session.isActive()).isTrue();
        assertThat(session.getStatus()).isEqualTo(ListeningSessionStatus.IN_PROGRESS);

        session.startEvaluating(startedAt.plusMinutes(5));

        assertThat(session.isActive()).isFalse();
        assertThat(session.getStatus()).isEqualTo(ListeningSessionStatus.EVALUATING);
        assertThat(session.getActiveKey()).isNull();

        session.recordLearning(true, 1_000, startedAt.plusMinutes(6));
        assertThat(session.getCompletedItemCount()).isEqualTo(1);
        assertThat(session.getEvaluatedItemCount()).isEqualTo(1);

        session.complete(startedAt.plusMinutes(7));
        assertThat(session.getStatus()).isEqualTo(ListeningSessionStatus.COMPLETED);
    }
}
