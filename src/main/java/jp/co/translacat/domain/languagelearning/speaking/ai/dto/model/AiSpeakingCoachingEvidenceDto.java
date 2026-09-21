package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

import java.util.List;

public record AiSpeakingCoachingEvidenceDto(
        String turnId,
        int turnIndex,
        Integer recordingRevision,
        String transcriptExcerpt,
        String transcriptHash,
        String referenceAssistantTurnId,
        List<AiSpeakingAssistanceUsageDto> assistanceUsage,
        String sourceProvenance,
        boolean verbatimAccuracyVerified
) { }
