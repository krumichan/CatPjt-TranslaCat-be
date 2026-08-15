package jp.co.translacat.domain.languagelearning.speaking.evaluation.repository;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.entity.SpeakingEvaluationMetric;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpeakingEvaluationMetricRepository
        extends JpaRepository<SpeakingEvaluationMetric, Long> {

    List<SpeakingEvaluationMetric> findAllByEvaluationIdOrderByMetricTypeAsc(
            Long evaluationId
    );
}
