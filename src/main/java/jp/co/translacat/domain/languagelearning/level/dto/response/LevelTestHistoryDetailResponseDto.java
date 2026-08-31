package jp.co.translacat.domain.languagelearning.level.dto.response;

import java.util.List;

public record LevelTestHistoryDetailResponseDto(
        LevelTestHistoryItemResponseDto summary,
        List<LevelTestItemDetailResponseDto> items
) {
}
