package jp.co.translacat.domain.languagelearning.ai.dto.model;

public record WritingCorrectionDto(
        String original,
        String corrected,
        String category,
        BilingualMessageDto explanation
) {
}
