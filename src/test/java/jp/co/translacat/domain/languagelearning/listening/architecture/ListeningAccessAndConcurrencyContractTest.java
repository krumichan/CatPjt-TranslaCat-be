package jp.co.translacat.domain.languagelearning.listening.architecture;

import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import jp.co.translacat.domain.languagelearning.listening.attempt.entity.ListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.listening.session.entity.ListeningSession;
import jp.co.translacat.domain.languagelearning.listening.session.repository.ListeningSessionRepository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ListeningAccessAndConcurrencyContractTest {

    @Test
    void ownedSessionMutationUsesPessimisticWriteLock() throws Exception {
        Method method = ListeningSessionRepository.class.getMethod(
                "findOwnedLockedByIdAndUserId",
                Long.class,
                Long.class
        );

        assertThat(method.getAnnotation(Lock.class))
                .isNotNull()
                .extracting(Lock::value)
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void attemptMutationUsesPessimisticWriteLock() throws Exception {
        Method method = ListeningItemAttemptRepository.class.getMethod(
                "findLockedById",
                Long.class
        );

        assertThat(method.getAnnotation(Lock.class))
                .isNotNull()
                .extracting(Lock::value)
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void sessionKeepsActiveAndIdempotencyUniqueConstraints() {
        Table table = ListeningSession.class.getAnnotation(Table.class);

        assertThat(constraints(table))
                .containsEntry(
                        "uk_ll_listening_session_user_key",
                        Set.of("user_id", "idempotency_key")
                )
                .containsEntry(
                        "uk_ll_listening_session_user_active",
                        Set.of("user_id", "active_key")
                );
    }

    @Test
    void attemptKeepsItemAndIdempotencyUniqueConstraints() {
        Table table = ListeningItemAttempt.class.getAnnotation(Table.class);

        assertThat(constraints(table))
                .containsEntry(
                        "uk_ll_listening_attempt_session_key",
                        Set.of("session_id", "idempotency_key")
                )
                .containsEntry(
                        "uk_ll_listening_attempt_item_purpose",
                        Set.of("item_id", "evaluation_purpose")
                );
    }

    private java.util.Map<String, Set<String>> constraints(Table table) {
        return Arrays.stream(table.uniqueConstraints())
                .collect(java.util.stream.Collectors.toMap(
                        UniqueConstraint::name,
                        constraint -> Set.of(constraint.columnNames())
                ));
    }
}
