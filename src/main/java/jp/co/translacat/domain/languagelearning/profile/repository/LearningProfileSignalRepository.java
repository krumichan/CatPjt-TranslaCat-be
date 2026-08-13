package jp.co.translacat.domain.languagelearning.profile.repository;

import jp.co.translacat.domain.languagelearning.common.enums.ProfileSignalType;
import jp.co.translacat.domain.languagelearning.profile.entity.LearningProfileSignal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningProfileSignalRepository extends JpaRepository<LearningProfileSignal, Long> {
    Optional<LearningProfileSignal> findByUserIdAndTypeAndKey(Long userId, ProfileSignalType type, String key);
    List<LearningProfileSignal> findAllByUserIdAndTypeOrderByOccurrenceCountDesc(Long userId, ProfileSignalType type);
}
