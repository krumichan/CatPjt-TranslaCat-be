package jp.co.translacat.domain.chat.ai.dto.server;

public record ChatAiResponsePlan(
        Long aiMemberId,
        ChatAiReplyRequestDto request
) {
}
