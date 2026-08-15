package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

import java.util.List;

public record AiSpeakingEvaluationTurnDto(
        String turnId,
        int turnIndex,
        String transcript,
        double sttConfidence,
        double durationSeconds,
        List<AiSpeakingSttSegmentDto> segments,
        String audioReference,
        boolean audioAvailable,
        AiSpeakingAudioQualitySignalsDto audioQualitySignals,
        boolean excludedFromEvaluation,
        List<AiSpeakingAssistanceUsageDto> assistanceUsage
) {
}
