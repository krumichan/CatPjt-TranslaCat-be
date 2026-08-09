package jp.co.translacat.domain.chat.ai.dto.response;

import jp.co.translacat.domain.chat.ai.entity.ChatAiSystemSetting;

import java.time.LocalTime;

public record ChatAiSystemSettingResponseDto(
        int maxAiMembersPerRoom,
        int conversationResponseRate,
        int conversationCooldownSeconds,
        int conversationMinHumanMessagesAfterAi,
        int revivalFirstDelayHours,
        int revivalSecondDelayHours,
        int revivalThirdDelayHours,
        LocalTime revivalAllowedStartTime,
        LocalTime revivalAllowedEndTime,
        int contextMaxMessages,
        int contextMaxCharacters,
        int replyMaxCharacters,
        int mentionRateLimitCount,
        int mentionRateLimitWindowSeconds
) {
    public static ChatAiSystemSettingResponseDto from(ChatAiSystemSetting setting) {
        return new ChatAiSystemSettingResponseDto(
                setting.getMaxAiMembersPerRoom(),
                setting.getConversationResponseRate(),
                setting.getConversationCooldownSeconds(),
                setting.getConversationMinHumanMessagesAfterAi(),
                setting.getRevivalFirstDelayHours(),
                setting.getRevivalSecondDelayHours(),
                setting.getRevivalThirdDelayHours(),
                setting.getRevivalAllowedStartTime(),
                setting.getRevivalAllowedEndTime(),
                setting.getContextMaxMessages(),
                setting.getContextMaxCharacters(),
                setting.getReplyMaxCharacters(),
                setting.getMentionRateLimitCount(),
                setting.getMentionRateLimitWindowSeconds()
        );
    }
}
