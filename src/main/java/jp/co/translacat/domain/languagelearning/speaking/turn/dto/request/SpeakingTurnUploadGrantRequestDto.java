package jp.co.translacat.domain.languagelearning.speaking.turn.dto.request;

public record SpeakingTurnUploadGrantRequestDto(
        int turnIndex,
        Integer problemIndex,
        Integer attemptIndex,
        String idempotencyKey
) {
}
