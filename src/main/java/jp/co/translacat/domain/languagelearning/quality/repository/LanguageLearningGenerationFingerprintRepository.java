package jp.co.translacat.domain.languagelearning.quality.repository;

import jp.co.translacat.domain.languagelearning.quality.common.LanguageLearningContentSource;
import jp.co.translacat.domain.languagelearning.quality.entity.LanguageLearningGenerationFingerprint;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface LanguageLearningGenerationFingerprintRepository
        extends JpaRepository<LanguageLearningGenerationFingerprint, Long> {

    List<LanguageLearningGenerationFingerprint>
    findTop80ByUserIdAndLearningLanguageAndSourceTypeAndGeneratedAtGreaterThanEqualOrderByGeneratedAtDesc(
            Long userId,
            String learningLanguage,
            LanguageLearningContentSource sourceType,
            LocalDateTime from
    );

    List<LanguageLearningGenerationFingerprint>
    findTop40ByUserIdAndLearningLanguageAndSourceTypeInAndGeneratedAtGreaterThanEqualOrderByGeneratedAtDesc(
            Long userId,
            String learningLanguage,
            Collection<LanguageLearningContentSource> sourceTypes,
            LocalDateTime from
    );

    List<LanguageLearningGenerationFingerprint>
    findTop200ByUserIdAndLearningLanguageAndGeneratedAtGreaterThanEqualOrderByGeneratedAtDesc(
            Long userId,
            String learningLanguage,
            LocalDateTime from
    );

    boolean existsByUserIdAndLearningLanguageAndContentHashAndGeneratedAtGreaterThanEqual(
            Long userId,
            String learningLanguage,
            String contentHash,
            LocalDateTime from
    );

    List<LanguageLearningGenerationFingerprint>
    findTop40ByUserIdAndLearningLanguageAndSourceTypeAndSourceIdStartingWithOrderByGeneratedAtDesc(
            Long userId,
            String learningLanguage,
            LanguageLearningContentSource sourceType,
            String sourceIdPrefix
    );
}
