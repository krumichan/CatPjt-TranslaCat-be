package jp.co.translacat.domain.languagelearning.profile.repository;

import jakarta.persistence.LockModeType;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.profile.entity.LearningProfileEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface LearningProfileEvidenceRepository
        extends JpaRepository<LearningProfileEvidence, Long> {

    Optional<LearningProfileEvidence> findByUserIdAndSourceAndPatternKeyAndDirection(
            Long userId,
            LearningSource source,
            String patternKey,
            String direction
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LearningProfileEvidence> findOneByUserIdAndSourceAndPatternKeyAndDirection(
            Long userId, LearningSource source, String patternKey, String direction);
}
