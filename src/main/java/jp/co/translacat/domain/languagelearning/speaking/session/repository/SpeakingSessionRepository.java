package jp.co.translacat.domain.languagelearning.speaking.session.repository;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingSessionStatus;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SpeakingSessionRepository
        extends JpaRepository<SpeakingSession, Long> {

    Optional<SpeakingSession> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SpeakingSession> findOneByIdAndUserId(
            Long id,
            Long userId
    );

    Optional<SpeakingSession> findByUserIdAndCreateIdempotencyKey(
            Long userId,
            String createIdempotencyKey
    );

    Optional<SpeakingSession> findFirstByUserIdAndStatusOrderByStartedAtDesc(
            Long userId,
            SpeakingSessionStatus status
    );

    List<SpeakingSession> findAllByUserIdAndLearningDate(
            Long userId,
            LocalDate learningDate
    );

    long countByUserIdAndLearningDate(
            Long userId,
            LocalDate learningDate
    );

    List<SpeakingSession> findAllByUserIdAndLearningDateBetweenOrderByLearningDateDescStartedAtDesc(
            Long userId,
            LocalDate from,
            LocalDate to
    );

    List<SpeakingSession> findAllByStatus(
            SpeakingSessionStatus status
    );

    List<SpeakingSession> findAllByStatusAndLastActivityAtBefore(
            SpeakingSessionStatus status,
            LocalDateTime before
    );

    List<SpeakingSession> findAllByOpeningAssistantAudioRetentionUntilBeforeAndOpeningAssistantAudioObjectKeyIsNotNull(
            LocalDateTime before
    );
}
