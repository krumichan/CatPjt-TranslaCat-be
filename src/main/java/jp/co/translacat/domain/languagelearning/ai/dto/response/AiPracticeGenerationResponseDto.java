package jp.co.translacat.domain.languagelearning.ai.dto.response;

import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeGeneratedQuestionDto;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;

import java.util.List;

public record AiPracticeGenerationResponseDto(
        String requestId,
        String promptVersion,
        PracticeDomain domain,
        String mode,
        int complexityBand,
        List<PracticeGeneratedQuestionDto> questions
) {
}
