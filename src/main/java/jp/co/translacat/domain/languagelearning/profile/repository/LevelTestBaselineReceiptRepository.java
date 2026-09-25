package jp.co.translacat.domain.languagelearning.profile.repository;

import jakarta.persistence.LockModeType;
import jp.co.translacat.domain.languagelearning.profile.entity.LevelTestBaselineReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface LevelTestBaselineReceiptRepository extends JpaRepository<LevelTestBaselineReceipt, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LevelTestBaselineReceipt> findLockedByUserId(Long userId);
}
