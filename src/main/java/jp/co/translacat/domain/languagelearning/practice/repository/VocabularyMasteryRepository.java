package jp.co.translacat.domain.languagelearning.practice.repository;

import jp.co.translacat.domain.languagelearning.practice.entity.VocabularyMastery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VocabularyMasteryRepository extends JpaRepository<VocabularyMastery, Long> {
    Optional<VocabularyMastery> findByUserIdAndCanonicalKey(Long userId, String canonicalKey);
    List<VocabularyMastery> findAllByUserIdOrderByScoreAsc(Long userId);
}
