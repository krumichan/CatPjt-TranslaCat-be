package jp.co.translacat.domain.languagelearning.listening.session.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import jp.co.translacat.domain.languagelearning.listening.attempt.entity.ListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningDailySetRepository;
import jp.co.translacat.domain.languagelearning.listening.session.entity.ListeningSession;
import jp.co.translacat.domain.languagelearning.listening.session.repository.ListeningSessionRepository;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListeningSessionLockServiceTest {

    @Test
    void locksAndRefreshesDailySetBeforeSessionBeforeAttempt() {
        EntityManager em = mock(EntityManager.class);
        ListeningItemAttemptRepository attempts = mock(ListeningItemAttemptRepository.class);
        ListeningDailySet dailySet = mock(ListeningDailySet.class);
        ListeningSession session = mock(ListeningSession.class);
        ListeningItemAttempt attempt = mock(ListeningItemAttempt.class);
        when(attempt.getSession()).thenReturn(session);
        when(session.getDailySet()).thenReturn(dailySet);
        when(attempts.findById(3L)).thenReturn(Optional.of(attempt));
        ListeningSessionLockService locks = new ListeningSessionLockService(
                mock(ListeningDailySetRepository.class), mock(ListeningSessionRepository.class), attempts, em);

        assertThat(locks.attempt(3L)).isSameAs(attempt);

        InOrder order = inOrder(em);
        order.verify(em).getLockMode(dailySet);
        order.verify(em).refresh(dailySet, LockModeType.PESSIMISTIC_WRITE);
        order.verify(em).getLockMode(session);
        order.verify(em).refresh(session, LockModeType.PESSIMISTIC_WRITE);
        order.verify(em).getLockMode(attempt);
        order.verify(em).refresh(attempt, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void nestedFinalizationDoesNotRefreshAwayAlreadyLockedLocalChanges() {
        EntityManager em = mock(EntityManager.class);
        ListeningItemAttemptRepository attempts = mock(ListeningItemAttemptRepository.class);
        ListeningDailySet dailySet = mock(ListeningDailySet.class);
        ListeningSession session = mock(ListeningSession.class);
        ListeningItemAttempt attempt = mock(ListeningItemAttempt.class);
        when(attempt.getSession()).thenReturn(session);
        when(session.getDailySet()).thenReturn(dailySet);
        when(attempts.findById(3L)).thenReturn(Optional.of(attempt));
        when(em.getLockMode(dailySet)).thenReturn(LockModeType.PESSIMISTIC_WRITE);
        when(em.getLockMode(session)).thenReturn(LockModeType.PESSIMISTIC_WRITE);
        when(em.getLockMode(attempt)).thenReturn(LockModeType.PESSIMISTIC_WRITE);
        ListeningSessionLockService locks = new ListeningSessionLockService(
                mock(ListeningDailySetRepository.class), mock(ListeningSessionRepository.class), attempts, em);

        locks.attempt(3L);

        verify(em, never()).refresh(dailySet, LockModeType.PESSIMISTIC_WRITE);
        verify(em, never()).refresh(session, LockModeType.PESSIMISTIC_WRITE);
        verify(em, never()).refresh(attempt, LockModeType.PESSIMISTIC_WRITE);
    }
}
