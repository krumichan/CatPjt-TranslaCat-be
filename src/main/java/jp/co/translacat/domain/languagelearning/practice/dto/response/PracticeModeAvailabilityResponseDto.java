package jp.co.translacat.domain.languagelearning.practice.dto.response;

public record PracticeModeAvailabilityResponseDto(
        String mode, boolean generationAvailable, String reason
) {
}
