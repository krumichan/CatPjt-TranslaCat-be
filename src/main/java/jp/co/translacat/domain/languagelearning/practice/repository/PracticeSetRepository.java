package jp.co.translacat.domain.languagelearning.practice.repository;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import jp.co.translacat.domain.languagelearning.practice.enums.PracticeGenerationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PracticeSetRepository extends JpaRepository<PracticeSet, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PracticeSet> findLockedById(Long id);

    List<PracticeSet> findTop20ByGenerationStatusOrderByIdAsc(PracticeGenerationStatus status);

    List<PracticeSet> findTop20ByGenerationStatusAndGenerationStartedAtBeforeOrderByGenerationStartedAtAsc(
            PracticeGenerationStatus status, LocalDateTime cutoff
    );
    Optional<PracticeSet> findByUserIdAndLearningDateAndDomainAndMode(
            Long userId, LocalDate learningDate, PracticeDomain domain, String mode
    );
    Optional<PracticeSet> findByIdAndUserId(Long id, Long userId);
    List<PracticeSet> findTop5ByUserIdAndDomainAndModeAndStatusOrderByLearningDateDescIdDesc(
            Long userId, PracticeDomain domain, String mode,
            jp.co.translacat.domain.languagelearning.common.enums.PracticeSetStatus status
    );
    List<PracticeSet> findAllByUserIdAndDomainAndLearningDateBetweenOrderByLearningDateDescIdDesc(
            Long userId, PracticeDomain domain, LocalDate from, LocalDate to
    );
    List<PracticeSet> findAllByUserIdAndLearningDate(Long userId, LocalDate learningDate);
    List<PracticeSet> findAllByUserIdAndLearningDateAndDomainOrderByIdAsc(
            Long userId, LocalDate learningDate, PracticeDomain domain
    );
}
