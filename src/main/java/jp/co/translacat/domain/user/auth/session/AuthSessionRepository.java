package jp.co.translacat.domain.user.auth.session;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {
    boolean existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(String id, Long userId, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthSession session where session.id = :id and session.userId = :userId")
    Optional<AuthSession> lockByIdAndUserId(@Param("id") String id, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("update AuthSession session set session.revokedAt = :now where session.userId = :userId and session.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
