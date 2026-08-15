package jp.co.translacat.domain.languagelearning.activity.repository;

import jp.co.translacat.domain.languagelearning.activity.entity.LearningActivity;
import jp.co.translacat.domain.languagelearning.common.enums.LearningActivityStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LearningActivityRepository
        extends JpaRepository<LearningActivity, Long> {

    Optional<LearningActivity> findBySourceAndReferenceId(
            LearningSource source,
            String referenceId
    );

    List<LearningActivity> findAllByUserIdAndLearningDateBetweenOrderByLearningDateDesc(
            Long userId,
            LocalDate from,
            LocalDate to
    );

    List<LearningActivity> findTop30ByUserIdAndStatusOrderByCompletedAtDesc(
            Long userId,
            LearningActivityStatus status
    );

    List<LearningActivity> findAllByUserIdAndSourceAndLearningDateBetweenOrderByLearningDateDesc(
            Long userId,
            LearningSource source,
            LocalDate from,
            LocalDate to
    );
}
