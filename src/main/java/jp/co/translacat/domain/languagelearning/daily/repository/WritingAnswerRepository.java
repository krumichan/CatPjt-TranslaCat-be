package jp.co.translacat.domain.languagelearning.daily.repository;

import jp.co.translacat.domain.languagelearning.daily.entity.WritingAnswer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WritingAnswerRepository
        extends JpaRepository<WritingAnswer, Long>, WritingAnswerRepositoryCustom {

    Optional<WritingAnswer> findByDailyItemIdAndAttemptDate(Long itemId, LocalDate attemptDate);

    boolean existsByDailyItemId(Long itemId);

    List<WritingAnswer> findAllByDailyItemIdOrderByAttemptDateAsc(Long itemId);
}
