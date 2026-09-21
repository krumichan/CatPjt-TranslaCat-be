package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

public record AiSpeakingCoachingItemDto(
        String observationId,
        String kind,
        AiSpeakingCoachingEvidenceDto evidence,
        String message,
        String suggestedExpression,
        boolean suggestionIsLearnerEvidence
) { }
