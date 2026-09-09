package jp.co.translacat.domain.languagelearning.quality.service;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.quality.common.LanguageLearningContentSource;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityContext;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityHistoryItem;
import jp.co.translacat.domain.languagelearning.quality.entity.LanguageLearningGenerationFingerprint;
import jp.co.translacat.domain.languagelearning.quality.repository.LanguageLearningGenerationFingerprintRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GenerationDiversityContextService {

    private final LanguageLearningGenerationFingerprintRepository repository;
    private final LanguageLearningJsonCodec jsonCodec;

    @Transactional(readOnly = true)
    public DiversityContext contextForSources(
            Long userId,
            String learningLanguage,
            LanguageLearningContentSource sourceType,
            Map<String, String> sourceContents
    ) {
        DiversityContext base = context(userId, learningLanguage, sourceType);
        if (sourceContents.isEmpty()) {
            return base;
        }
        var fingerprints = repository.findAllByUserIdAndLearningLanguageAndSourceTypeAndSourceIdInOrderByGeneratedAtDesc(
                userId, learningLanguage, sourceType, sourceContents.keySet());
        var currentSession = sourceContents.entrySet().stream()
                .skip(Math.max(0, sourceContents.size() - 40L))
                .map(entry -> {
                    var fingerprint = fingerprints.stream()
                            .filter(value -> entry.getKey().equals(value.getSourceId())).findFirst().orElse(null);
                    if (fingerprint == null) {
                        return new DiversityHistoryItem(sourceType, entry.getValue(), null, null, null, null,
                                List.of(), null, 0);
                    }
                    DiversityHistoryItem metadata = map(fingerprint);
                    return new DiversityHistoryItem(sourceType, entry.getValue(), metadata.contentHash(),
                            metadata.scenarioCategory(), metadata.communicativeIntent(), metadata.taskArchetype(),
                            metadata.grammarFocusCodes(), metadata.semanticSummary(), metadata.ageDays());
                }).toList();
        return new DiversityContext(currentSession, base.sameFeatureRecent(), base.crossFeatureRecent(),
                base.exactContentHashes90d());
    }

    @Transactional(readOnly = true)
    public DiversityContext context(
            Long userId,
            String learningLanguage,
            LanguageLearningContentSource sourceType
    ) {
        LocalDateTime now = LocalDateTime.now();
        var sameFeature = repository
                .findTop80ByUserIdAndLearningLanguageAndSourceTypeAndGeneratedAtGreaterThanEqualOrderByGeneratedAtDesc(
                        userId,
                        learningLanguage,
                        sourceType,
                        now.minusDays(30)
                );
        var crossSources = Arrays.stream(
                        LanguageLearningContentSource.values()
                )
                .filter(value -> value != sourceType)
                .toList();
        var crossFeature = repository
                .findTop40ByUserIdAndLearningLanguageAndSourceTypeInAndGeneratedAtGreaterThanEqualOrderByGeneratedAtDesc(
                        userId,
                        learningLanguage,
                        crossSources,
                        now.minusDays(14)
                );
        List<String> exactHashes = repository
                .findTop200ByUserIdAndLearningLanguageAndGeneratedAtGreaterThanEqualOrderByGeneratedAtDesc(
                        userId,
                        learningLanguage,
                        now.minusDays(90)
                )
                .stream()
                .map(LanguageLearningGenerationFingerprint::getContentHash)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return new DiversityContext(
                List.of(),
                sameFeature.stream().map(this::map).toList(),
                crossFeature.stream().map(this::map).toList(),
                exactHashes
        );
    }

    @Transactional(readOnly = true)
    public DiversityContext levelTestContext(
            Long userId,
            String learningLanguage,
            Long sessionId
    ) {
        DiversityContext base = context(
                userId,
                learningLanguage,
                LanguageLearningContentSource.LEVEL_TEST
        );
        var currentSession = repository
                .findTop40ByUserIdAndLearningLanguageAndSourceTypeAndSourceIdStartingWithOrderByGeneratedAtDesc(
                        userId,
                        learningLanguage,
                        LanguageLearningContentSource.LEVEL_TEST,
                        sessionId + ":"
                )
                .stream()
                .map(this::map)
                .toList();

        return new DiversityContext(
                currentSession,
                base.sameFeatureRecent(),
                base.crossFeatureRecent(),
                base.exactContentHashes90d()
        );
    }

    private DiversityHistoryItem map(
            LanguageLearningGenerationFingerprint value
    ) {
        List<String> grammarFocus = value.getGrammarFocusJson() == null
                ? List.of()
                : jsonCodec.read(
                        value.getGrammarFocusJson(),
                        new TypeReference<List<String>>() {
                        }
                );
        return new DiversityHistoryItem(
                value.getSourceType(),
                value.getContentExcerpt() == null
                        ? value.getContentHash()
                        : value.getContentExcerpt(),
                value.getContentHash(),
                value.getScenarioCategory(),
                value.getCommunicativeIntent(),
                value.getTaskArchetype(),
                grammarFocus,
                value.getSemanticSummary(),
                (int) ChronoUnit.DAYS.between(
                        value.getGeneratedAt(),
                        LocalDateTime.now()
                )
        );
    }
}
