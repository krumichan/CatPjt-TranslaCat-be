package jp.co.translacat.domain.user.auth.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GoogleAuthIntentRepository extends JpaRepository<GoogleAuthIntent, String> {
    @Query("select new jp.co.translacat.domain.user.auth.account.GoogleAuthIntentSnapshot(intent.userId, intent.expiresAt, intent.consumedAt) "
            + "from GoogleAuthIntent intent where intent.tokenHash = :hash")
    Optional<GoogleAuthIntentSnapshot> findStateByHash(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select intent from GoogleAuthIntent intent where intent.tokenHash = :hash")
    Optional<GoogleAuthIntent> findLockedByHash(@Param("hash") String hash);
}
