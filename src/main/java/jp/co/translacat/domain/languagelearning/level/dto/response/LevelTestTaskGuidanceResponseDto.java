package jp.co.translacat.domain.languagelearning.level.dto.response;

import java.util.List;

public record LevelTestTaskGuidanceResponseDto(
        List<String> providedFacts,
        List<String> requiredIntents,
        List<String> responseConstraints
) {
}
