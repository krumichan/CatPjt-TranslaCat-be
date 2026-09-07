package jp.co.translacat.domain.languagelearning.practice.repository;

import jp.co.translacat.domain.languagelearning.practice.entity.PracticeMetricScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeMetricScoreRepository extends JpaRepository<PracticeMetricScore, Long> {
    List<PracticeMetricScore> findAllByPracticeSetIdOrderBySkillTagAsc(Long practiceSetId);
}
