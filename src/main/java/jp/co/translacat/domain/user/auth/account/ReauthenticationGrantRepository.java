package jp.co.translacat.domain.user.auth.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReauthenticationGrantRepository extends JpaRepository<ReauthenticationGrant, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select proof from ReauthenticationGrant proof where proof.tokenHash = :hash")
    Optional<ReauthenticationGrant> findLockedByHash(@Param("hash") String hash);
}
