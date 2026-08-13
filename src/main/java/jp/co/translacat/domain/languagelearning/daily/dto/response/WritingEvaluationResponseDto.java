package jp.co.translacat.domain.languagelearning.daily.dto.response;

import jp.co.translacat.domain.languagelearning.ai.dto.model.BilingualMessageDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.WritingCorrectionDto;
import jp.co.translacat.domain.languagelearning.common.enums.WritingEvaluationContext;

import java.time.LocalDateTime;
import java.util.List;

public record WritingEvaluationResponseDto(
        Long evaluationId,
        WritingEvaluationContext context,
        int overall,
        int meaning,
        int grammar,
        int vocabulary,
        int naturalness,
        int expression,
        List<BilingualMessageDto> strengths,
        List<BilingualMessageDto> weaknesses,
        List<WritingCorrectionDto> corrections,
        List<String> recommendedAnswers,
        BilingualMessageDto explanation,
        String evaluationRubricVersion,
        String scoringPolicyVersion,
        String promptVersion,
        LocalDateTime evaluatedAt
) {
}
