package jp.co.translacat.domain.languagelearning.growth.model;

import jp.co.translacat.domain.languagelearning.profile.dto.response.KeywordMasteryResponseDto;
import jp.co.translacat.domain.languagelearning.profile.dto.response.ProfileSignalResponseDto;

import java.util.List;
import java.util.Map;

public record GrowthSnapshot(long userId, String sourceInstanceId, long sequence, boolean preview,
                             GrowthProfileSnapshot profile, List<KeywordMasteryResponseDto> masteries,
                             Map<String, List<ProfileSignalResponseDto>> signals) {
    public GrowthSnapshot {
        if (userId <= 0 || sequence < 0 || masteries == null || signals == null)
            throw new IllegalArgumentException("성장 조회 응답이 올바르지 않습니다.");
        masteries = List.copyOf(masteries);
        signals = Map.copyOf(signals);
    }
}
