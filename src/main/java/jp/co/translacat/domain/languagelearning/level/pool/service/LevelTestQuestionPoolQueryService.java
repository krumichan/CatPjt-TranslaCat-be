package jp.co.translacat.domain.languagelearning.level.pool.service;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;
import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionPool;
import jp.co.translacat.domain.languagelearning.level.pool.repository.LevelTestQuestionPoolRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LevelTestQuestionPoolQueryService {

    private final LevelTestQuestionPoolRepository repository;

    @Transactional(readOnly = true)
    public Optional<LevelTestQuestionPool> findReusable(
            String originLanguage,
            String learningLanguage,
            LevelTestDomain domain,
            LevelTestItemType itemType,
            int complexityBand,
            String policyVersion,
            String modelConfigVersion,
            List<String> excludedContentHashes
    ) {
        return findReusableCandidates(
                originLanguage,
                learningLanguage,
                domain,
                itemType,
                complexityBand,
                policyVersion,
                modelConfigVersion,
                excludedContentHashes
        ).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<LevelTestQuestionPool> findReusableCandidates(
            String originLanguage,
            String learningLanguage,
            LevelTestDomain domain,
            LevelTestItemType itemType,
            int complexityBand,
            String policyVersion,
            String modelConfigVersion,
            List<String> excludedContentHashes
    ) {
        Set<String> excluded = excludedContentHashes == null
                ? Set.of()
                : new HashSet<>(excludedContentHashes);
        return repository
                .findTop50ByActiveTrueAndOriginLanguageAndLearningLanguageAndDomainAndItemTypeAndComplexityBandAndPolicyVersionAndModelConfigVersionOrderByUsageCountAscLastUsedAtAscIdAsc(
                        originLanguage,
                        learningLanguage,
                        domain,
                        itemType,
                        complexityBand,
                        policyVersion,
                        modelConfigVersion
                )
                .stream()
                .filter(value -> !excluded.contains(value.getContentHash()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> recentScenarioCategories(
            String originLanguage,
            String learningLanguage,
            LevelTestDomain domain,
            LevelTestItemType itemType
    ) {
        return repository
                .findTop200ByActiveTrueAndOriginLanguageAndLearningLanguageAndDomainAndItemTypeOrderByGeneratedAtDesc(
                        originLanguage,
                        learningLanguage,
                        domain,
                        itemType
                )
                .stream()
                .map(LevelTestQuestionPool::getScenarioCategory)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> recentContentHashes(
            String originLanguage,
            String learningLanguage
    ) {
        return repository
                .findTop200ByOriginLanguageAndLearningLanguageOrderByGeneratedAtDesc(
                        originLanguage,
                        learningLanguage
                )
                .stream()
                .map(LevelTestQuestionPool::getContentHash)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }


    @Transactional(readOnly = true)
    public List<LevelTestQuestionPool> healthScanAfter(Long afterId, int limit) {
        return repository.findByActiveTrueAndIdGreaterThanOrderByIdAsc(
                afterId == null ? 0L : Math.max(0L, afterId),
                PageRequest.of(0, Math.max(1, Math.min(1000, limit)))
        );
    }

    @Transactional(readOnly = true)
    public List<LevelTestQuestionPool> pendingRepairs(int limit) {
        return repository
                .findTop100ByActiveFalseAndQuarantineReasonIsNotNullAndReplacementPoolQuestionIdIsNullOrderByQuarantinedAtAscIdAsc()
                .stream()
                .limit(Math.max(1, Math.min(100, limit)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<LevelTestQuestionPool> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<LevelTestQuestionPool> findDuplicate(
            String originLanguage,
            String learningLanguage,
            String contentHash,
            String similarityKey
    ) {
        Optional<LevelTestQuestionPool> byContent = repository
                .findByOriginLanguageAndLearningLanguageAndContentHash(
                        originLanguage,
                        learningLanguage,
                        contentHash
                );
        if (byContent.isPresent()) {
            return byContent;
        }
        return repository.findByOriginLanguageAndLearningLanguageAndSimilarityKey(
                originLanguage,
                learningLanguage,
                similarityKey
        );
    }

    @Transactional(readOnly = true)
    public long totalCount(
            String originLanguage,
            String learningLanguage,
            String policyVersion,
            String modelConfigVersion
    ) {
        return repository
                .countByActiveTrueAndOriginLanguageAndLearningLanguageAndPolicyVersionAndModelConfigVersion(
                        originLanguage,
                        learningLanguage,
                        policyVersion,
                        modelConfigVersion
                );
    }

    @Transactional(readOnly = true)
    public long bucketCount(
            String originLanguage,
            String learningLanguage,
            LevelTestDomain domain,
            LevelTestItemType itemType,
            int complexityBand,
            String policyVersion,
            String modelConfigVersion
    ) {
        return repository
                .countByActiveTrueAndOriginLanguageAndLearningLanguageAndDomainAndItemTypeAndComplexityBandAndPolicyVersionAndModelConfigVersion(
                        originLanguage,
                        learningLanguage,
                        domain,
                        itemType,
                        complexityBand,
                        policyVersion,
                        modelConfigVersion
                );
    }

    @Transactional(readOnly = true)
    public PoolCounts counts(
            String originLanguage,
            String learningLanguage,
            LevelTestDomain domain,
            LevelTestItemType itemType,
            int complexityBand,
            String policyVersion,
            String modelConfigVersion
    ) {
        long total = repository
                .countByActiveTrueAndOriginLanguageAndLearningLanguageAndPolicyVersionAndModelConfigVersion(
                        originLanguage,
                        learningLanguage,
                        policyVersion,
                        modelConfigVersion
                );
        long bucket = repository
                .countByActiveTrueAndOriginLanguageAndLearningLanguageAndDomainAndItemTypeAndComplexityBandAndPolicyVersionAndModelConfigVersion(
                        originLanguage,
                        learningLanguage,
                        domain,
                        itemType,
                        complexityBand,
                        policyVersion,
                        modelConfigVersion
                );
        return new PoolCounts(total, bucket);
    }

    public record PoolCounts(long total, long bucket) {
    }
}
