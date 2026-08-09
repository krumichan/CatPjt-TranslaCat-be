package jp.co.translacat.domain.chat.ai.dto.server;

public record ChatAiRevivalClaim(
        Long activityId,
        Long roomId,
        Long aiMemberId,
        String claimToken,
        long cycleVersion,
        int attemptNumber,
        String requestId
) {
}
