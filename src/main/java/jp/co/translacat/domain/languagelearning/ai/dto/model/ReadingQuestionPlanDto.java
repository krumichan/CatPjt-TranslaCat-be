package jp.co.translacat.domain.languagelearning.ai.dto.model;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDifficulty;

public record ReadingQuestionPlanDto(
        int globalOrder,
        String skillTag,
        PracticeDifficulty difficulty,
        int complexityBand,
        String clueQuote,
        String questionFocus,
        String unstatedInference
) {
}
