package jp.co.translacat.domain.chat.ai.dto.request;

import java.time.LocalTime;

public record ChatAiSystemSettingUpdateRequestDto(
        Integer maxAiMembersPerRoom,
        Integer conversationResponseRate,
        Integer conversationCooldownSeconds,
        Integer conversationMinHumanMessagesAfterAi,
        Integer revivalFirstDelayHours,
        Integer revivalSecondDelayHours,
        Integer revivalThirdDelayHours,
        LocalTime revivalAllowedStartTime,
        LocalTime revivalAllowedEndTime,
        Integer contextMaxMessages,
        Integer contextMaxCharacters,
        Integer replyMaxCharacters,
        Integer mentionRateLimitCount,
        Integer mentionRateLimitWindowSeconds
) {
}
