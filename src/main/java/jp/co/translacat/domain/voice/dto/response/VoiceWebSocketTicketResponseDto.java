package jp.co.translacat.domain.voice.dto.response;

public record VoiceWebSocketTicketResponseDto(
        String ticket,
        int expiresInSeconds
) {
}
