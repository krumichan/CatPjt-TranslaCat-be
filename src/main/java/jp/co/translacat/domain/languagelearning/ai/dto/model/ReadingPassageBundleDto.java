package jp.co.translacat.domain.languagelearning.ai.dto.model;

import java.util.List;

/**
 * Private verified passage draft; never returned by a learner-facing DTO.
 */
public record ReadingPassageBundleDto(
        String passageId,
        String promptVersion,
        String passageSha256,
        List<ReadingQuestionPlanDto> questionPlans,
        List<PracticeGeneratedQuestionDto> questions
) {
}
