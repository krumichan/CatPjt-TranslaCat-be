package jp.co.translacat.domain.chat.ai.dto.server;

public record ChatAiReplyResponseDto(
        String requestId,
        boolean shouldRespond,
        String reply,
        String languageCode
) {
}
