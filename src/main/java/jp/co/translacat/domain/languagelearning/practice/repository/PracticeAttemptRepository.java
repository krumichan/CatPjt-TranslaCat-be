package jp.co.translacat.domain.languagelearning.practice.repository;

import jp.co.translacat.domain.languagelearning.practice.entity.PracticeAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PracticeAttemptRepository extends JpaRepository<PracticeAttempt, Long> {
    List<PracticeAttempt> findAllByQuestionIdOrderByAttemptNoAsc(Long questionId);
    Optional<PracticeAttempt> findByQuestionIdAndAttemptNo(Long questionId, int attemptNo);
    long countByQuestionPracticeSetIdAndAttemptNo(Long practiceSetId, int attemptNo);
    long countByQuestionPracticeSetIdAndAttemptNoAndCorrectTrue(Long practiceSetId, int attemptNo);
    List<PracticeAttempt> findAllByQuestionPracticeSetIdAndAttemptNoOrderByQuestionOrderNoAsc(
            Long practiceSetId, int attemptNo
    );
    List<PracticeAttempt> findTop30ByQuestionPracticeSetUserIdAndQuestionPracticeSetDomainAndCorrectFalseOrderBySubmittedAtDesc(
            Long userId, jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain domain
    );
}
