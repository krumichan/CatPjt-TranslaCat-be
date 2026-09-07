package jp.co.translacat.domain.languagelearning.practice.repository;

import jp.co.translacat.domain.languagelearning.practice.entity.PracticeMetricScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeMetricScoreRepository extends JpaRepository<PracticeMetricScore, Long> {
    List<PracticeMetricScore> findAllByPracticeSetIdOrderBySkillTagAsc(Long practiceSetId);
    List<PracticeMetricScore> findAllByPracticeSetUserIdAndPracticeSetDomainAndPracticeSetLearningDateBetweenOrderByPracticeSetLearningDateAsc(
            Long userId,
            jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain domain,
            java.time.LocalDate from,
            java.time.LocalDate to
    );
}
