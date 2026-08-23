package jp.co.translacat.domain.languagelearning.listening.recommendation.repository;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningProfileMetric;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningRecommendationStatus;
import jp.co.translacat.domain.languagelearning.listening.recommendation.entity.LearningRecommendation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface LearningRecommendationRepository
        extends JpaRepository<LearningRecommendation, Long> {

    List<LearningRecommendation>
    findTop2ByUserIdAndLearningLanguageAndStatusOrderByPriorityAscCreatedAtDesc(
            Long userId,
            String learningLanguage,
            ListeningRecommendationStatus status
    );

    List<LearningRecommendation>
    findAllByUserIdAndLearningLanguageAndStatus(
            Long userId,
            String learningLanguage,
            ListeningRecommendationStatus status
    );

    Optional<LearningRecommendation>
    findByUserIdAndLearningLanguageAndTargetMetricAndCalculationVersion(
            Long userId,
            String learningLanguage,
            ListeningProfileMetric metric,
            String calculationVersion
    );

    Optional<LearningRecommendation> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LearningRecommendation> findLockedById(Long id);
}
