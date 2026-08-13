package jp.co.translacat.domain.languagelearning.keyword.repository;

import jp.co.translacat.domain.languagelearning.keyword.entity.KeywordMastery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KeywordMasteryRepository extends JpaRepository<KeywordMastery, Long> {
    Optional<KeywordMastery> findByUserIdAndCanonicalKey(Long userId, String canonicalKey);
    List<KeywordMastery> findAllByUserIdOrderByScoreAsc(Long userId);
}
