package jp.co.translacat.domain.languagelearning.ai.dto.response;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DailyWritingGeneratedItemDto;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversitySummary;

import java.util.List;

public record AiDailyWritingGenerationResponseDto(
        String requestId,
        String promptVersion,
        List<DailyWritingGeneratedItemDto> items,
        String contentDiversityPolicyVersion,
        String languageComplexityPolicyVersion,
        DiversitySummary diversitySummary
) {

    public AiDailyWritingGenerationResponseDto(
            String requestId,
            String promptVersion,
            List<DailyWritingGeneratedItemDto> items
    ) {
        this(
                requestId,
                promptVersion,
                items,
                null,
                null,
                null
        );
    }
}
