package jp.co.translacat.domain.languagelearning.activity.repository;

import jp.co.translacat.domain.languagelearning.activity.entity.EvaluationMetricHistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvaluationMetricHistoryRepository
        extends JpaRepository<EvaluationMetricHistory, Long> {

    List<EvaluationMetricHistory> findAllByActivityIdOrderByMetricTypeAsc(
            Long activityId
    );

    void deleteAllByActivityId(Long activityId);

    List<EvaluationMetricHistory> findAllByActivityUserIdAndActivityLearningDateBetween(
            Long userId,
            java.time.LocalDate from,
            java.time.LocalDate to
    );
}
