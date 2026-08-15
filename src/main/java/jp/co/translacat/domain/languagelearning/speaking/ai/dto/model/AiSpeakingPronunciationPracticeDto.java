package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

import java.util.List;

public record AiSpeakingPronunciationPracticeDto(
        String target,
        String practicePhrase,
        String reason,
        List<String> evidenceTurnIds
) {
}
