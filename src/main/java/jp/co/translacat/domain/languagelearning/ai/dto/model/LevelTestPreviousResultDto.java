package jp.co.translacat.domain.languagelearning.ai.dto.model;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;

public record LevelTestPreviousResultDto(
        int questionNumber,
        LevelTestDomain domain,
        LevelTestItemType itemType,
        Integer score,
        int complexityBand,
        boolean evaluable
) {
}
