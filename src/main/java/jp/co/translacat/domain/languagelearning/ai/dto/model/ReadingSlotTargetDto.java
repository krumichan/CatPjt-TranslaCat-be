package jp.co.translacat.domain.languagelearning.ai.dto.model;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDifficulty;

/**
 * Immutable five-slot Reading curriculum sent with every progressive request.
 */
public record ReadingSlotTargetDto(
        int globalOrder,
        PracticeDifficulty difficulty,
        int complexityBand,
        String skillTag
) {
}
