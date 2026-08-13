package jp.co.translacat.domain.languagelearning.ai.dto.response;

import jp.co.translacat.domain.languagelearning.ai.dto.model.BilingualMessageDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.ProfileSignalsDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.WritingCorrectionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.WritingEvaluationScoresDto;

import java.util.List;

public record AiWritingEvaluationResponseDto(
        String requestId,
        WritingEvaluationScoresDto scores,
        List<BilingualMessageDto> strengths,
        List<BilingualMessageDto> weaknesses,
        List<WritingCorrectionDto> corrections,
        List<String> recommendedAnswers,
        BilingualMessageDto explanation,
        ProfileSignalsDto profileSignals,
        String evaluationRubricVersion,
        String scoringPolicyVersion,
        String promptVersion
) {
}
