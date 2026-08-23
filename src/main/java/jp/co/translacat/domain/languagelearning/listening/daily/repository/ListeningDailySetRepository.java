package jp.co.translacat.domain.languagelearning.listening.daily.repository;

import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ListeningDailySetRepository
        extends JpaRepository<ListeningDailySet, Long> {

    Optional<ListeningDailySet> findByUserIdAndLearningDateAndLearningLanguage(
            Long userId,
            LocalDate learningDate,
            String learningLanguage
    );

    List<ListeningDailySet>
    findAllByUserIdAndLearningDateBetweenOrderByLearningDateDesc(
            Long userId,
            LocalDate from,
            LocalDate to
    );
}
