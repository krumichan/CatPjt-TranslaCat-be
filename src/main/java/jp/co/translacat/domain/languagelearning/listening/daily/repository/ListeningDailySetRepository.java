package jp.co.translacat.domain.languagelearning.listening.daily.repository;

import jakarta.persistence.LockModeType;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningLearningMode;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ListeningDailySetRepository
        extends JpaRepository<ListeningDailySet, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ListeningDailySet> findLockedById(Long id);

    Optional<ListeningDailySet> findByUserIdAndLearningDateAndLearningLanguageAndLearningMode(
            Long userId,
            LocalDate learningDate,
            String learningLanguage,
            ListeningLearningMode learningMode
    );

    List<ListeningDailySet> findAllByUserIdAndLearningDateAndLearningLanguageOrderByIdAsc(
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
