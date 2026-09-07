package jp.co.translacat.domain.languagelearning.practice.repository;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PracticeSetRepository extends JpaRepository<PracticeSet, Long> {
    Optional<PracticeSet> findByUserIdAndLearningDateAndDomainAndMode(
            Long userId, LocalDate learningDate, PracticeDomain domain, String mode
    );
    Optional<PracticeSet> findByIdAndUserId(Long id, Long userId);
    List<PracticeSet> findAllByUserIdAndLearningDateBetweenOrderByLearningDateDescIdDesc(
            Long userId, LocalDate from, LocalDate to
    );
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
