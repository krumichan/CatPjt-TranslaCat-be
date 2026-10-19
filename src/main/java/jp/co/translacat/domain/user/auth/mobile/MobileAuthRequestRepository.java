package jp.co.translacat.domain.user.auth.mobile;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MobileAuthRequestRepository extends JpaRepository<MobileAuthRequest, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from MobileAuthRequest request where request.id = :id")
    Optional<MobileAuthRequest> lockById(@Param("id") String id);

    @Query("""
            select request.id from MobileAuthRequest request
            where request.expiresAt <= :now or request.codeExpiresAt <= :now
            order by request.expiresAt, request.id
            """)
    List<String> findExpiredIds(@Param("now") Instant now, Pageable page);
}
