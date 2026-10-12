package jp.co.translacat.domain.user.auth.email;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface EmailVerificationChallengeRepository extends JpaRepository<EmailVerificationChallenge, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EmailVerificationChallenge c where c.id = :id")
    Optional<EmailVerificationChallenge> findLockedById(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EmailVerificationChallenge c where c.proofHash = :hash")
    Optional<EmailVerificationChallenge> findLockedByProofHash(@Param("hash") String hash);

    Optional<EmailVerificationChallenge> findFirstByEmailOrderByCreatedAtDesc(String email);

    long countByEmailAndCreatedAtAfter(String email, Instant after);

    @Modifying
    @Query("update EmailVerificationChallenge c set c.status = :invalidated "
            + "where c.email = :email and c.consumedAt is null and c.status in :statuses")
    int invalidateUnconsumed(@Param("email") String email,
                            @Param("statuses") Collection<EmailVerificationChallenge.Status> statuses,
                            @Param("invalidated") EmailVerificationChallenge.Status invalidated);
}
