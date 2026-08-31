package jp.co.translacat.domain.languagelearning.level.pool.repository;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;
import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionPool;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LevelTestQuestionPoolRepository
        extends JpaRepository<LevelTestQuestionPool, Long> {

    List<LevelTestQuestionPool>
    findTop50ByActiveTrueAndOriginLanguageAndLearningLanguageAndDomainAndItemTypeAndComplexityBandAndPolicyVersionAndModelConfigVersionOrderByUsageCountAscLastUsedAtAscIdAsc(
            String originLanguage,
            String learningLanguage,
            LevelTestDomain domain,
            LevelTestItemType itemType,
            int complexityBand,
            String policyVersion,
            String modelConfigVersion
    );

    long countByActiveTrueAndOriginLanguageAndLearningLanguageAndPolicyVersionAndModelConfigVersion(
            String originLanguage,
            String learningLanguage,
            String policyVersion,
            String modelConfigVersion
    );

    long countByActiveTrueAndOriginLanguageAndLearningLanguageAndDomainAndItemTypeAndComplexityBandAndPolicyVersionAndModelConfigVersion(
            String originLanguage,
            String learningLanguage,
            LevelTestDomain domain,
            LevelTestItemType itemType,
            int complexityBand,
            String policyVersion,
            String modelConfigVersion
    );

    List<LevelTestQuestionPool>
    findTop200ByOriginLanguageAndLearningLanguageOrderByGeneratedAtDesc(
            String originLanguage,
            String learningLanguage
    );

    List<LevelTestQuestionPool>
    findTop200ByActiveTrueAndOriginLanguageAndLearningLanguageAndDomainAndItemTypeOrderByGeneratedAtDesc(
            String originLanguage,
            String learningLanguage,
            LevelTestDomain domain,
            LevelTestItemType itemType
    );

    List<LevelTestQuestionPool> findByActiveTrueAndIdGreaterThanOrderByIdAsc(
            Long id,
            Pageable pageable
    );

    List<LevelTestQuestionPool>
    findTop100ByActiveFalseAndQuarantineReasonIsNotNullAndReplacementPoolQuestionIdIsNullOrderByQuarantinedAtAscIdAsc();

    Optional<LevelTestQuestionPool>
    findByOriginLanguageAndLearningLanguageAndContentHash(
            String originLanguage,
            String learningLanguage,
            String contentHash
    );

    Optional<LevelTestQuestionPool>
    findByOriginLanguageAndLearningLanguageAndSimilarityKey(
            String originLanguage,
            String learningLanguage,
            String similarityKey
    );
}
