package jp.co.translacat.domain.user.pushinstallation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PushRegistryLockRepository extends JpaRepository<PushRegistryLock, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select guard from PushRegistryLock guard where guard.id = 1")
    Optional<PushRegistryLock> lockRegistry();
}
