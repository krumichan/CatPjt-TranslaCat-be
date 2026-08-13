package jp.co.translacat.domain.languagelearning.daily.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.DailySetStatus;

import java.time.LocalDate;
import java.util.List;

public record DailyWritingSetResponseDto(
        Long dailySetId,
        LocalDate learningDate,
        String snapshotId,
        DailySetStatus status,
        int sentenceCount,
        int regenerationCount,
        String promptVersion,
        boolean reviewAvailable,
        List<DailyWritingItemResponseDto> items
) {
}
