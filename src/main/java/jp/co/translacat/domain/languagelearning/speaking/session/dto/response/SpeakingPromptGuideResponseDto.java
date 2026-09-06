package jp.co.translacat.domain.languagelearning.speaking.session.dto.response;

import java.util.List;

public record SpeakingPromptGuideResponseDto(
        String scriptText,
        List<String> providedFacts,
        List<String> requiredIntents,
        List<String> responseConstraints
) {
    public static SpeakingPromptGuideResponseDto empty() {
        return new SpeakingPromptGuideResponseDto(null, List.of(), List.of(), List.of());
    }
}
