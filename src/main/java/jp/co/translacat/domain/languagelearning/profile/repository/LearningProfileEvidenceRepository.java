package jp.co.translacat.domain.languagelearning.profile.repository;

import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.profile.entity.LearningProfileEvidence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LearningProfileEvidenceRepository
        extends JpaRepository<LearningProfileEvidence, Long> {

    Optional<LearningProfileEvidence> findByUserIdAndSourceAndPatternKeyAndDirection(
            Long userId,
            LearningSource source,
            String patternKey,
            String direction
    );


}
