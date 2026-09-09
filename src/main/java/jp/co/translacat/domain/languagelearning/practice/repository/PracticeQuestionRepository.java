package jp.co.translacat.domain.languagelearning.practice.repository;

import jp.co.translacat.domain.languagelearning.practice.entity.PracticeQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PracticeQuestionRepository extends JpaRepository<PracticeQuestion, Long> {
    long countByPracticeSetId(Long practiceSetId);
    List<PracticeQuestion> findAllByPracticeSetIdOrderByOrderNoAsc(Long practiceSetId);
    Optional<PracticeQuestion> findByIdAndPracticeSetUserId(Long id, Long userId);
}
