package jp.co.translacat.domain.languagelearning.listening.session.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import jp.co.translacat.domain.languagelearning.listening.attempt.entity.ListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningDailySetRepository;
import jp.co.translacat.domain.languagelearning.listening.session.entity.ListeningSession;
import jp.co.translacat.domain.languagelearning.listening.session.repository.ListeningSessionRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes mutations in Daily Set, Session, Attempt, then Response order. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ListeningSessionLockService {

    private final ListeningDailySetRepository dailySetRepository;
    private final ListeningSessionRepository sessionRepository;
    private final ListeningItemAttemptRepository attemptRepository;
    private final EntityManager entityManager;

    public ListeningDailySet ownedDailySet(Long userId, Long dailySetId) {
        ListeningDailySet dailySet = dailySetRepository.findById(dailySetId)
                .filter(value -> value.getUser().getId().equals(userId))
                .orElseThrow(() -> new BusinessException(
                        "Listening Daily Set을 찾을 수 없습니다.", LanguageLearningErrorCode.DAILY_SET_NOT_FOUND));
        refreshLocked(dailySet);
        return dailySet;
    }

    public ListeningSession ownedSession(Long userId, Long sessionId) {
        ListeningSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> notFound("Listening Session을 찾을 수 없습니다."));
        return lockSession(session);
    }

    public ListeningItemAttempt ownedAttempt(Long userId, Long attemptId) {
        ListeningItemAttempt attempt = attemptRepository.findByIdAndSessionUserId(attemptId, userId)
                .orElseThrow(() -> new BusinessException(
                        "Listening Attempt를 찾을 수 없습니다.", LanguageLearningErrorCode.DAILY_ITEM_NOT_FOUND));
        return lockAttempt(attempt);
    }

    public ListeningItemAttempt attempt(Long attemptId) {
        return lockAttempt(attemptRepository.findById(attemptId).orElseThrow());
    }

    private ListeningItemAttempt lockAttempt(ListeningItemAttempt attempt) {
        lockSession(attempt.getSession());
        refreshLocked(attempt);
        return attempt;
    }

    private ListeningSession lockSession(ListeningSession session) {
        refreshLocked(session.getDailySet());
        refreshLocked(session);
        return session;
    }

    private void refreshLocked(Object entity) {
        // A locking query alone can return an already managed, stale instance
        // after waiting for a concurrent transaction. Refresh on first lock;
        // nested finalization must keep this transaction's unsaved mutations.
        if (entityManager.getLockMode(entity) != LockModeType.PESSIMISTIC_WRITE) {
            entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        }
    }

    private BusinessException notFound(String message) {
        return new BusinessException(message, LanguageLearningErrorCode.SESSION_NOT_FOUND);
    }
}
