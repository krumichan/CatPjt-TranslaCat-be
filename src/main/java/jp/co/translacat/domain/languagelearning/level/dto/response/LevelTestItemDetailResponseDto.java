package jp.co.translacat.domain.languagelearning.level.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;

import java.util.List;
import java.util.Map;

public record LevelTestItemDetailResponseDto(
        int questionNumber,
        LevelTestDomain domain,
        LevelTestItemType itemType,
        int complexityBand,
        String promptText,
        String selectedOptionKey,
        List<String> selectedOptionKeys,
        String textAnswer,
        boolean audioSubmitted,
        boolean evaluable,
        Integer score,
        Double confidence,
        List<Map<String, Object>> metrics,
        List<String> strengths,
        List<String> improvements,
        String reasonCode
) {
}
