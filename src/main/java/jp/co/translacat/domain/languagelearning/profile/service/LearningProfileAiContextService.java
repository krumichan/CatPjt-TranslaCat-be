package jp.co.translacat.domain.languagelearning.profile.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyPerformanceDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.KeywordMasteryDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.LearningProfileSummaryDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.WritingSkillScoresDto;
import jp.co.translacat.domain.languagelearning.common.enums.ProfileSignalType;
import jp.co.translacat.domain.languagelearning.keyword.repository.KeywordMasteryRepository;
import jp.co.translacat.domain.languagelearning.profile.entity.LearningProfile;
import jp.co.translacat.domain.languagelearning.profile.repository.LearningProfileRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningProfileAiContextService {

    private static final int GRAMMAR_WEAKNESS_LIMIT = 10;
    private static final int ERROR_PATTERN_LIMIT = 10;
    private static final int PROFILE_SIGNAL_LIMIT = 10;
    private static final int KEYWORD_MASTERY_LIMIT = 30;

    private final LearningProfileRepository profileRepository;
    private final KeywordMasteryRepository masteryRepository;
    private final LearningProfileSignalService signalService;
    private final ObjectMapper objectMapper;

    public LearningProfileSummaryDto buildSummary(Long userId) {
        LearningProfile profile = profileRepository
                .findByUserId(userId)
                .orElse(null);

        if (profile == null) {
            return null;
        }

        return new LearningProfileSummaryDto(
                profile.getProfileVersion(),
                profile.getBaseLevelScore(),
                toSkillScores(profile),
                signalService.getKeys(
                        userId,
                        ProfileSignalType.GRAMMAR_WEAKNESS,
                        GRAMMAR_WEAKNESS_LIMIT
                ),
                getKeywordMasteries(userId),
                new DifficultyPerformanceDto(
                        profile.getReviewPerformance(),
                        profile.getNormalPerformance(),
                        profile.getChallengePerformance()
                ),
                signalService.getKeys(
                        userId,
                        ProfileSignalType.ERROR_PATTERN,
                        ERROR_PATTERN_LIMIT
                ),
                profile.getTrend(),
                profile.getConfidence(),
                signalService.getKeys(
                        userId,
                        ProfileSignalType.STRENGTH,
                        PROFILE_SIGNAL_LIMIT
                ),
                signalService.getKeys(
                        userId,
                        ProfileSignalType.WEAKNESS,
                        PROFILE_SIGNAL_LIMIT
                ),
                signalService.getKeys(
                        userId,
                        ProfileSignalType.RECOMMENDED_FOCUS,
                        PROFILE_SIGNAL_LIMIT
                ),
                readAdditionalSignals(profile.getAdditionalSignalsJson())
        );
    }

    private List<KeywordMasteryDto> getKeywordMasteries(Long userId) {
        return masteryRepository.findAllByUserIdOrderByScoreAsc(userId)
                .stream()
                .limit(KEYWORD_MASTERY_LIMIT)
                .map(mastery -> new KeywordMasteryDto(
                        mastery.getCanonicalKey(),
                        mastery.getScore()
                ))
                .toList();
    }

    private WritingSkillScoresDto toSkillScores(LearningProfile profile) {
        boolean empty = profile.getMeaningScore() == null
                && profile.getGrammarScore() == null
                && profile.getVocabularyScore() == null
                && profile.getNaturalnessScore() == null
                && profile.getExpressionScore() == null;
        if (empty) {
            return null;
        }

        return new WritingSkillScoresDto(
                valueOrZero(profile.getMeaningScore()),
                valueOrZero(profile.getGrammarScore()),
                valueOrZero(profile.getVocabularyScore()),
                valueOrZero(profile.getNaturalnessScore()),
                valueOrZero(profile.getExpressionScore())
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readAdditionalSignals(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private double valueOrZero(Double value) {
        return value == null ? 0 : value;
    }
}
