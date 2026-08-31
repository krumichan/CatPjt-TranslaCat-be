package jp.co.translacat.domain.languagelearning.ai.dto.model;

import java.util.List;
import java.util.Map;

public record LevelTestInternalAnswerKeyDto(
        String correctOptionKey,
        List<String> correctOrder,
        String selectionPolicy,
        Map<String, Integer> optionScores
) {
}
