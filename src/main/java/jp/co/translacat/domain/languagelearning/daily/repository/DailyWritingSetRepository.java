package jp.co.translacat.domain.languagelearning.daily.repository;

import jp.co.translacat.domain.languagelearning.common.enums.DailySetStatus;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyWritingSetRepository extends JpaRepository<DailyWritingSet, Long> {

    boolean existsByUserId(Long userId);

    Optional<DailyWritingSet> findByUserIdAndLearningDateAndWritingType(
            Long userId,
            LocalDate date,
            DailyWritingType writingType
    );

    List<DailyWritingSet> findAllByUserIdAndLearningDate(
            Long userId,
            LocalDate date
    );

    List<DailyWritingSet> findAllByUserIdAndStatusOrderByLearningDateDesc(Long userId, DailySetStatus status);


    List<DailyWritingSet> findTop30ByUserIdOrderByLearningDateDesc(Long userId);

    List<DailyWritingSet> findAllByUserIdAndLearningDateBetweenOrderByLearningDateDesc(
            Long userId,
            LocalDate from,
            LocalDate to
    );
}
