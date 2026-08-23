package jp.co.translacat.domain.languagelearning.listening.profile.repository;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningProfileMetric;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.profile.entity.ListeningMetricHistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ListeningMetricHistoryRepository
        extends JpaRepository<ListeningMetricHistory, Long> {

    List<ListeningMetricHistory>
    findTop30ByUserIdAndLearningLanguageAndMetricTypeAndProfileAppliedTrueOrderByCreatedAtDesc(
            Long userId,
            String learningLanguage,
            ListeningProfileMetric metricType
    );

    List<ListeningMetricHistory>
    findTop30ByUserIdAndLearningLanguageAndTaskTypeAndMetricTypeAndProfileAppliedTrueOrderByCreatedAtDesc(
            Long userId,
            String learningLanguage,
            ListeningTaskType taskType,
            ListeningProfileMetric metricType
    );

    List<ListeningMetricHistory>
    findAllByUserIdAndLearningLanguageAndProfileAppliedTrueOrderByCreatedAtDesc(
            Long userId,
            String learningLanguage
    );

    boolean existsByReferenceEvaluationIdAndMetricType(
            String referenceEvaluationId,
            ListeningProfileMetric metricType
    );

    boolean existsByReferenceActivityIdAndProfileAppliedTrue(
            String referenceActivityId
    );
}
