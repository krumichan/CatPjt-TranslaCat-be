package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.dto.request.ChatAiSystemSettingUpdateRequestDto;
import jp.co.translacat.domain.chat.ai.dto.response.ChatAiSystemSettingResponseDto;
import jp.co.translacat.domain.chat.ai.entity.ChatAiSystemSetting;
import jp.co.translacat.domain.chat.ai.repository.ChatAiSystemSettingRepository;
import jp.co.translacat.domain.chat.ai.support.ChatAiErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatAiSystemSettingService {

    private final ChatAiSystemSettingRepository repository;

    @Transactional
    public ChatAiSystemSettingResponseDto getSettings() {
        return ChatAiSystemSettingResponseDto.from(getOrCreateEntity());
    }

    @Transactional
    public ChatAiSystemSettingResponseDto updateSettings(
            ChatAiSystemSettingUpdateRequestDto request
    ) {
        if (request == null) {
            throw invalidSetting("AI 시스템 설정 요청은 필수입니다.");
        }

        ChatAiSystemSetting setting = getOrCreateEntity();
        validateMerged(setting, request);
        setting.update(
                request.maxAiMembersPerRoom(),
                request.conversationResponseRate(),
                request.conversationCooldownSeconds(),
                request.conversationMinHumanMessagesAfterAi(),
                request.responseDelayEnabled(),
                request.responseDelayMinMillis(),
                request.responseDelayMaxMillis(),
                request.revivalFirstDelayHours(),
                request.revivalSecondDelayHours(),
                request.revivalThirdDelayHours(),
                request.revivalAllowedStartTime(),
                request.revivalAllowedEndTime(),
                request.contextMaxMessages(),
                request.contextMaxCharacters(),
                request.replyMaxCharacters(),
                request.mentionRateLimitCount(),
                request.mentionRateLimitWindowSeconds()
        );
        return ChatAiSystemSettingResponseDto.from(setting);
    }

    @Transactional
    public ChatAiSystemSetting getOrCreateEntity() {
        ChatAiSystemSetting setting = repository.findById(
                        ChatAiSystemSetting.DEFAULT_ID
                )
                .orElseGet(() -> repository.save(
                        ChatAiSystemSetting.createDefault()
                ));
        setting.ensureResponseDelayDefaults();
        return setting;
    }

    private void validateMerged(
            ChatAiSystemSetting current,
            ChatAiSystemSettingUpdateRequestDto request
    ) {
        int maxAiMembers = value(
                request.maxAiMembersPerRoom(),
                current.getMaxAiMembersPerRoom()
        );
        int responseRate = value(
                request.conversationResponseRate(),
                current.getConversationResponseRate()
        );
        int cooldown = value(
                request.conversationCooldownSeconds(),
                current.getConversationCooldownSeconds()
        );
        int minHumanMessages = value(
                request.conversationMinHumanMessagesAfterAi(),
                current.getConversationMinHumanMessagesAfterAi()
        );
        int responseDelayMinMillis = value(
                request.responseDelayMinMillis(),
                current.getResponseDelayMinMillis()
        );
        int responseDelayMaxMillis = value(
                request.responseDelayMaxMillis(),
                current.getResponseDelayMaxMillis()
        );
        int firstDelay = value(
                request.revivalFirstDelayHours(),
                current.getRevivalFirstDelayHours()
        );
        int secondDelay = value(
                request.revivalSecondDelayHours(),
                current.getRevivalSecondDelayHours()
        );
        int thirdDelay = value(
                request.revivalThirdDelayHours(),
                current.getRevivalThirdDelayHours()
        );
        LocalTime start = request.revivalAllowedStartTime() == null
                ? current.getRevivalAllowedStartTime()
                : request.revivalAllowedStartTime();
        LocalTime end = request.revivalAllowedEndTime() == null
                ? current.getRevivalAllowedEndTime()
                : request.revivalAllowedEndTime();
        int contextMessages = value(
                request.contextMaxMessages(),
                current.getContextMaxMessages()
        );
        int contextCharacters = value(
                request.contextMaxCharacters(),
                current.getContextMaxCharacters()
        );
        int replyCharacters = value(
                request.replyMaxCharacters(),
                current.getReplyMaxCharacters()
        );
        int rateLimitCount = value(
                request.mentionRateLimitCount(),
                current.getMentionRateLimitCount()
        );
        int rateLimitWindow = value(
                request.mentionRateLimitWindowSeconds(),
                current.getMentionRateLimitWindowSeconds()
        );

        if (maxAiMembers < 1) {
            throw invalidSetting("방당 AI 최대 인원은 1명 이상이어야 합니다.");
        }
        if (responseRate < 0 || responseRate > 100) {
            throw invalidSetting("CONVERSATION 반응률은 0~100 사이여야 합니다.");
        }
        if (cooldown < 0) {
            throw invalidSetting("CONVERSATION Cooldown은 0초 이상이어야 합니다.");
        }
        if (minHumanMessages < 1) {
            throw invalidSetting("AI 발화 후 최소 사람 메시지 수는 1개 이상이어야 합니다.");
        }
        if (responseDelayMinMillis < 100
                || responseDelayMaxMillis < 100
                || responseDelayMinMillis > responseDelayMaxMillis
                || responseDelayMaxMillis > 10_000) {
            throw invalidSetting(
                    "AI 응답 지연은 100~10000ms 범위에서 최소값이 최대값 이하여야 합니다."
            );
        }
        if (firstDelay < 1 || secondDelay < 1 || thirdDelay < 1) {
            throw invalidSetting("REVIVAL 대기 시간은 1시간 이상이어야 합니다.");
        }
        if (!start.isBefore(end)) {
            throw invalidSetting("REVIVAL 허용 시작 시간은 종료 시간보다 빨라야 합니다.");
        }
        if (contextMessages < 1
                || contextCharacters < 1
                || replyCharacters < 1) {
            throw invalidSetting("AI Context 및 응답 제한값은 1 이상이어야 합니다.");
        }
        if (rateLimitCount < 1 || rateLimitWindow < 1) {
            throw invalidSetting("MENTION Rate Limit 값은 1 이상이어야 합니다.");
        }
    }

    private int value(Integer requested, int current) {
        return requested == null ? current : requested;
    }

    private BusinessException invalidSetting(String message) {
        return new BusinessException(
                message,
                ChatAiErrorCode.SETTING_INVALID
        );
    }
}
