package jp.co.translacat.domain.user.auth.email;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface EmailVerificationQuotaRepository extends JpaRepository<EmailVerificationQuota, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from EmailVerificationQuota q where q.id = 1")
    Optional<EmailVerificationQuota> findGlobalForUpdate();
}
