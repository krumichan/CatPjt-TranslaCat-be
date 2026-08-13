package jp.co.translacat.domain.languagelearning.ai.dto.response;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DailyWritingGeneratedItemDto;

import java.util.List;

public record AiDailyWritingGenerationResponseDto(
        String requestId,
        String promptVersion,
        List<DailyWritingGeneratedItemDto> items
) {
}
