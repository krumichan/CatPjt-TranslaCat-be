package jp.co.translacat.domain.languagelearning.profile.service;

import jp.co.translacat.domain.languagelearning.common.enums.LearningProfileState;
import jp.co.translacat.domain.languagelearning.common.enums.ProfileSignalType;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthSnapshot;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthReadGateway;
import jp.co.translacat.domain.languagelearning.practice.service.VocabularyMasteryQueryService;
import jp.co.translacat.domain.languagelearning.profile.dto.response.DifficultyPerformanceResponseDto;
import jp.co.translacat.domain.languagelearning.profile.dto.response.ProfileResponseDto;
import jp.co.translacat.domain.languagelearning.profile.dto.response.ProfileSignalResponseDto;
import jp.co.translacat.domain.languagelearning.profile.dto.response.SkillScoresResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 외부 Profile DTO는 유지한다. 공통 성장 데이터는 LL에서만 읽는다.
 */
@Service
@RequiredArgsConstructor
public class LearningProfileQueryService {
    private final GrowthReadGateway growth;
    private final VocabularyMasteryQueryService vocabulary;

    public ProfileResponseDto getProfile(Long userId) {
        GrowthSnapshot value = growth.snapshot(userId);
        var p = value.profile();
        if (p == null)
            return new ProfileResponseDto("PROFILE", LearningProfileState.LEVEL_TEST_REQUIRED, null, null, null,
                    new SkillScoresResponseDto(null, null, null, null, null),
                    new DifficultyPerformanceResponseDto(null, null, null), 0, "stable", List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(),
                    new jp.co.translacat.domain.languagelearning.practice.dto.response.VocabularyMasterySummaryResponseDto(
                            0, 0, 0, 0, 0, 0, 0, List.of()));
        return new ProfileResponseDto(p.profileVersion(), p.state(), p.baseLevelScore(), p.calibrationStartedDate(),
                p.calibrationCompletedDate(),
                new SkillScoresResponseDto(p.meaningScore(), p.grammarScore(), p.vocabularyScore(),
                        p.naturalnessScore(), p.expressionScore()),
                new DifficultyPerformanceResponseDto(p.reviewPerformance(), p.normalPerformance(),
                        p.challengePerformance()), p.confidence(), p.trend(),
                value.masteries().stream().limit(100).toList(), signals(value, ProfileSignalType.GRAMMAR_WEAKNESS),
                signals(value, ProfileSignalType.ERROR_PATTERN), signals(value, ProfileSignalType.STRENGTH),
                signals(value, ProfileSignalType.WEAKNESS), signals(value, ProfileSignalType.RECOMMENDED_FOCUS),
                vocabulary.get(userId));
    }

    private List<ProfileSignalResponseDto> signals(GrowthSnapshot value, ProfileSignalType type) {
        return value.signals().getOrDefault(type.name(), List.of()).stream().limit(20).toList();
    }
}
