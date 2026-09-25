package jp.co.translacat.domain.languagelearning.profile.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyPerformanceDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.KeywordMasteryDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.LearningProfileSummaryDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.WritingSkillScoresDto;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthSnapshot;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthReadGateway;
import jp.co.translacat.domain.languagelearning.profile.dto.response.ProfileSignalResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 아직 Core에 남은 기능의 AI 요청 DTO와 LL 읽기 모델 사이의 호환 어댑터다.
 */
@Service
@RequiredArgsConstructor
public class LearningProfileAiContextService {
    private final GrowthReadGateway growth;
    private final ObjectMapper mapper;

    public LearningProfileSummaryDto buildSummary(Long userId) {
        var value = growth.snapshot(userId);
        var p = value.profile();
        if (p == null) return null;
        WritingSkillScoresDto scores = p.meaningScore() == null
                && p.grammarScore() == null
                && p.vocabularyScore() == null
                && p.naturalnessScore() == null
                && p.expressionScore() == null ? null :
                new WritingSkillScoresDto(zero(p.meaningScore()), zero(p.grammarScore()), zero(p.vocabularyScore()),
                        zero(p.naturalnessScore()), zero(p.expressionScore()));
        return new LearningProfileSummaryDto(p.profileVersion(), p.baseLevelScore(), scores,
                keys(value, "GRAMMAR_WEAKNESS"), value.masteries()
                .stream()
                .limit(30)
                .map(v -> new KeywordMasteryDto(v.canonicalKey(), v.score()))
                .toList(),
                new DifficultyPerformanceDto(p.reviewPerformance(), p.normalPerformance(), p.challengePerformance()),
                keys(value, "ERROR_PATTERN"), p.trend(), p.confidence(), keys(value, "STRENGTH"),
                keys(value, "WEAKNESS"), keys(value, "RECOMMENDED_FOCUS"), additional(p.additionalSignalsJson()));
    }

    private List<String> keys(GrowthSnapshot value, String type) {
        return value.signals()
                .getOrDefault(type, List.of())
                .stream()
                .limit(10)
                .map(ProfileSignalResponseDto::key)
                .toList();
    }

    private double zero(Double value) {
        return value == null ? 0.0 : value;
    }

    private Map<String, Object> additional(String text) {
        if (text == null || text.isBlank()) return Map.of();
        try {
            return mapper.readValue(text, new TypeReference<Map<String, Object>>() {
            });
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("LL additionalSignals 응답 형식이 올바르지 않습니다.");
        }
    }
}
