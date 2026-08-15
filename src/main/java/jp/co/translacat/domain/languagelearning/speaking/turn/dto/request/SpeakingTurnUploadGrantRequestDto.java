package jp.co.translacat.domain.languagelearning.speaking.turn.dto.request;

public record SpeakingTurnUploadGrantRequestDto(
        int turnIndex,
        String idempotencyKey
) {
}
