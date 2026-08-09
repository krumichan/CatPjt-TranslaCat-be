package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.dto.server.ChatAiReplyRequestDto;
import jp.co.translacat.domain.chat.ai.dto.server.ChatAiResponsePlan;
import jp.co.translacat.domain.chat.ai.entity.ChatAiAgent;
import jp.co.translacat.domain.chat.ai.entity.ChatAiSystemSetting;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiSetting;
import jp.co.translacat.domain.chat.ai.enums.ChatAiMentionPermission;
import jp.co.translacat.domain.chat.ai.enums.ChatAiTriggerType;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiMemberRepository;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiSettingRepository;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageSenderType;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMessageSenderResponseDto;
import jp.co.translacat.domain.chat.openchat.profile.service.OpenChatMessageProfileService;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChatAiTriggerPlanner {

    private static final int AI_SERVER_HARD_MAX_CONTEXT_MESSAGES = 100;
    private static final int AI_SERVER_HARD_MAX_CONTEXT_CHARACTERS = 50_000;
    private static final int AI_SERVER_HARD_MAX_REPLY_CHARACTERS = 4_000;
    private static final int AI_SERVER_MESSAGE_MAX_CHARACTERS = 5_000;
    private static final int AI_SERVER_SENDER_NAME_MAX_CHARACTERS = 100;

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatRoomAiMemberRepository chatRoomAiMemberRepository;
    private final ChatRoomAiSettingRepository chatRoomAiSettingRepository;
    private final ChatAiSystemSettingService chatAiSystemSettingService;
    private final OpenChatMessageProfileService openChatMessageProfileService;
    private final ChatAiMentionParser mentionParser;
    private final ChatAiConversationGate conversationGate;

    public List<ChatAiResponsePlan> plan(Long triggerMessageId) {
        if (triggerMessageId == null) {
            return List.of();
        }

        ChatMessage triggerMessage = chatMessageRepository
                .findWithSenderById(triggerMessageId)
                .orElse(null);

        if (!isEligibleUserTrigger(triggerMessage)) {
            return List.of();
        }

        ChatRoom room = triggerMessage.getChatRoom();
        List<ChatRoomAiMember> aiMembers = activeAiMembers(room.getId());
        if (aiMembers.isEmpty()) {
            return List.of();
        }

        ChatRoomAiSetting roomSetting = chatRoomAiSettingRepository
                .findByChatRoomId(room.getId())
                .orElse(null);
        if (roomSetting == null) {
            return List.of();
        }

        ChatAiSystemSetting systemSetting =
                chatAiSystemSettingService.getOrCreateEntity();

        ChatRoomMember senderMember = chatRoomMemberRepository
                .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                        room.getId(),
                        triggerMessage.getSenderUser().getId()
                )
                .orElse(null);
        if (senderMember == null) {
            return List.of();
        }

        List<ChatRoomAiMember> mentionedMembers =
                mentionParser.findMentionedMembers(
                        triggerMessage.getContent(),
                        aiMembers
                );

        if (!mentionedMembers.isEmpty()) {
            return planMention(
                    triggerMessage,
                    senderMember,
                    mentionedMembers,
                    aiMembers,
                    roomSetting,
                    systemSetting
            );
        }

        return planConversation(
                triggerMessage,
                aiMembers,
                roomSetting,
                systemSetting
        );
    }

    private List<ChatAiResponsePlan> planMention(
            ChatMessage triggerMessage,
            ChatRoomMember senderMember,
            List<ChatRoomAiMember> mentionedMembers,
            List<ChatRoomAiMember> allAiMembers,
            ChatRoomAiSetting roomSetting,
            ChatAiSystemSetting systemSetting
    ) {
        if (!canMention(senderMember, roomSetting)) {
            return List.of();
        }

        int remaining = resolveRemainingMentionCapacity(
                triggerMessage,
                allAiMembers,
                systemSetting
        );
        if (remaining <= 0) {
            log.debug(
                    "AI mention rate limit reached. roomId={}, userId={}, messageId={}",
                    triggerMessage.getChatRoom().getId(),
                    triggerMessage.getSenderUser().getId(),
                    triggerMessage.getId()
            );
            return List.of();
        }

        return mentionedMembers.stream()
                .limit(remaining)
                .map(aiMember -> createPlan(
                        ChatAiTriggerType.MENTION,
                        triggerMessage,
                        aiMember,
                        systemSetting
                ))
                .toList();
    }

    private List<ChatAiResponsePlan> planConversation(
            ChatMessage triggerMessage,
            List<ChatRoomAiMember> aiMembers,
            ChatRoomAiSetting roomSetting,
            ChatAiSystemSetting systemSetting
    ) {
        if (!roomSetting.isConversationEnabled()) {
            return List.of();
        }

        ChatMessage lastAiMessage = chatMessageRepository
                .findTopByChatRoomIdAndSenderTypeAndStatusAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                        triggerMessage.getChatRoom().getId(),
                        ChatMessageSenderType.AI,
                        ChatMessageStatus.SENT,
                        triggerMessage.getId()
                )
                .orElse(null);

        if (!passesCooldown(
                triggerMessage,
                lastAiMessage,
                systemSetting.getConversationCooldownSeconds()
        )) {
            return List.of();
        }

        long humanMessageCount = countHumanMessagesAfterLastAi(
                triggerMessage,
                lastAiMessage
        );
        if (humanMessageCount
                < systemSetting.getConversationMinHumanMessagesAfterAi()) {
            return List.of();
        }

        if (!conversationGate.passesResponseRate(
                systemSetting.getConversationResponseRate()
        )) {
            return List.of();
        }

        ChatRoomAiMember candidate = selectConversationCandidate(
                triggerMessage,
                aiMembers
        );
        if (candidate == null) {
            return List.of();
        }

        return List.of(createPlan(
                ChatAiTriggerType.CONVERSATION,
                triggerMessage,
                candidate,
                systemSetting
        ));
    }

    private boolean isEligibleUserTrigger(ChatMessage message) {
        return message != null
                && message.isSent()
                && message.isUserMessage()
                && message.getSenderUser() != null
                && message.getChatRoom() != null
                && message.getChatRoom().getRoomType() != ChatRoomType.DIRECT
                && (message.getChatRoom().getRoomType() == ChatRoomType.GROUP
                || message.getChatRoom().getRoomType() == ChatRoomType.OPEN);
    }

    private List<ChatRoomAiMember> activeAiMembers(Long roomId) {
        return chatRoomAiMemberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNullOrderByJoinedAtAsc(
                        roomId
                )
                .stream()
                .filter(member -> member.getAiAgent() != null)
                .filter(member -> member.getAiAgent().isActive())
                .filter(member -> !member.getAiAgent().isDeleted())
                .toList();
    }

    private boolean canMention(
            ChatRoomMember senderMember,
            ChatRoomAiSetting roomSetting
    ) {
        if (roomSetting.getMentionPermission()
                == ChatAiMentionPermission.ALL_MEMBERS) {
            return true;
        }
        return senderMember.isOwner() || senderMember.isAdmin();
    }

    private int resolveRemainingMentionCapacity(
            ChatMessage triggerMessage,
            List<ChatRoomAiMember> aiMembers,
            ChatAiSystemSetting systemSetting
    ) {
        int limit = systemSetting.getMentionRateLimitCount();
        int windowSeconds = systemSetting.getMentionRateLimitWindowSeconds();
        LocalDateTime triggerAt = triggerMessage.getCreatedAt();
        if (triggerAt == null) {
            triggerAt = LocalDateTime.now();
        }
        LocalDateTime windowStart = triggerAt.minusSeconds(windowSeconds);

        List<ChatMessage> recentMessages = chatMessageRepository
                .findByChatRoomIdAndSenderUserIdAndSenderTypeAndStatusAndDeletedAtIsNullAndCreatedAtGreaterThanEqualAndIdLessThanOrderByIdDesc(
                        triggerMessage.getChatRoom().getId(),
                        triggerMessage.getSenderUser().getId(),
                        ChatMessageSenderType.USER,
                        ChatMessageStatus.SENT,
                        windowStart,
                        triggerMessage.getId()
                );

        int priorCalls = recentMessages.stream()
                .mapToInt(message -> mentionParser.countMentionTargets(
                        message.getContent(),
                        aiMembers
                ))
                .sum();

        return Math.max(0, limit - priorCalls);
    }

    private boolean passesCooldown(
            ChatMessage triggerMessage,
            ChatMessage lastAiMessage,
            int cooldownSeconds
    ) {
        if (lastAiMessage == null || cooldownSeconds <= 0) {
            return true;
        }
        if (lastAiMessage.getCreatedAt() == null
                || triggerMessage.getCreatedAt() == null) {
            return false;
        }
        return !triggerMessage.getCreatedAt().isBefore(
                lastAiMessage.getCreatedAt().plusSeconds(cooldownSeconds)
        );
    }

    private long countHumanMessagesAfterLastAi(
            ChatMessage triggerMessage,
            ChatMessage lastAiMessage
    ) {
        if (lastAiMessage == null) {
            return chatMessageRepository
                    .countByChatRoomIdAndSenderTypeAndStatusAndDeletedAtIsNullAndIdLessThanEqual(
                            triggerMessage.getChatRoom().getId(),
                            ChatMessageSenderType.USER,
                            ChatMessageStatus.SENT,
                            triggerMessage.getId()
                    );
        }

        return chatMessageRepository
                .countByChatRoomIdAndSenderTypeAndStatusAndDeletedAtIsNullAndIdGreaterThanAndIdLessThanEqual(
                        triggerMessage.getChatRoom().getId(),
                        ChatMessageSenderType.USER,
                        ChatMessageStatus.SENT,
                        lastAiMessage.getId(),
                        triggerMessage.getId()
                );
    }

    private ChatRoomAiMember selectConversationCandidate(
            ChatMessage triggerMessage,
            List<ChatRoomAiMember> aiMembers
    ) {
        if (aiMembers.isEmpty()) {
            return null;
        }
        int index = Math.floorMod(
                Long.hashCode(triggerMessage.getId()),
                aiMembers.size()
        );
        return aiMembers.get(index);
    }

    private ChatAiResponsePlan createPlan(
            ChatAiTriggerType triggerType,
            ChatMessage triggerMessage,
            ChatRoomAiMember aiMember,
            ChatAiSystemSetting systemSetting
    ) {
        ChatAiReplyRequestDto request = buildRequest(
                triggerType,
                triggerMessage,
                aiMember,
                systemSetting
        );
        return new ChatAiResponsePlan(aiMember.getId(), request);
    }

    private ChatAiReplyRequestDto buildRequest(
            ChatAiTriggerType triggerType,
            ChatMessage triggerMessage,
            ChatRoomAiMember aiMember,
            ChatAiSystemSetting systemSetting
    ) {
        int contextMaxMessages = Math.min(
                systemSetting.getContextMaxMessages(),
                AI_SERVER_HARD_MAX_CONTEXT_MESSAGES
        );
        int contextMaxCharacters = Math.min(
                systemSetting.getContextMaxCharacters(),
                AI_SERVER_HARD_MAX_CONTEXT_CHARACTERS
        );
        int replyMaxCharacters = Math.min(
                systemSetting.getReplyMaxCharacters(),
                AI_SERVER_HARD_MAX_REPLY_CHARACTERS
        );

        List<ChatMessage> contextSource = chatMessageRepository
                .findByChatRoomIdAndStatusAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                        triggerMessage.getChatRoom().getId(),
                        ChatMessageStatus.SENT,
                        triggerMessage.getId(),
                        PageRequest.of(0, contextMaxMessages)
                );

        List<ChatMessage> contextMessages = new ArrayList<>(contextSource);
        Collections.reverse(contextMessages);
        trimOldestMessagesToCharacterLimit(
                contextMessages,
                contextMaxCharacters
        );

        SenderContext senderContext = createSenderContext(
                triggerMessage,
                contextMessages
        );

        ChatAiAgent agent = aiMember.getAiAgent();
        ChatRoom room = triggerMessage.getChatRoom();
        String requestId = buildRequestId(
                triggerType,
                triggerMessage.getId(),
                aiMember.getId()
        );

        return new ChatAiReplyRequestDto(
                requestId,
                triggerType,
                new ChatAiReplyRequestDto.Room(
                        room.getId(),
                        room.getRoomType(),
                        room.getName(),
                        room.getDescription()
                ),
                new ChatAiReplyRequestDto.AiMember(
                        aiMember.getId(),
                        agent.getNickname(),
                        agent.getBio(),
                        agent.getPersonaPrompt(),
                        agent.getOriginalLanguageCode()
                ),
                new ChatAiReplyRequestDto.TriggerMessage(
                        triggerMessage.getId(),
                        senderContext.userAlias(triggerMessage),
                        senderContext.userName(triggerMessage),
                        triggerMessage.getContent(),
                        triggerMessage.getCreatedAt()
                ),
                contextMessages.stream()
                        .map(message -> toContextMessage(
                                message,
                                senderContext
                        ))
                        .toList(),
                contextMaxMessages,
                contextMaxCharacters,
                replyMaxCharacters
        );
    }

    private void trimOldestMessagesToCharacterLimit(
            List<ChatMessage> messages,
            int maxCharacters
    ) {
        int totalCharacters = messages.stream()
                .map(ChatMessage::getContent)
                .filter(content -> content != null)
                .mapToInt(String::length)
                .sum();

        while (totalCharacters > maxCharacters && !messages.isEmpty()) {
            ChatMessage removed = messages.removeFirst();
            if (removed.getContent() != null) {
                totalCharacters -= removed.getContent().length();
            }
        }
    }

    private SenderContext createSenderContext(
            ChatMessage triggerMessage,
            List<ChatMessage> contextMessages
    ) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (ChatMessage message : contextMessages) {
            if (message.getSenderUser() != null) {
                userIds.add(message.getSenderUser().getId());
            }
        }
        if (triggerMessage.getSenderUser() != null) {
            userIds.add(triggerMessage.getSenderUser().getId());
        }

        Map<Long, String> aliases = new LinkedHashMap<>();
        int aliasNumber = 1;
        for (Long userId : userIds) {
            aliases.put(userId, "member-" + aliasNumber++);
        }

        Map<Long, OpenChatMessageSenderResponseDto> openProfiles =
                triggerMessage.getChatRoom().getRoomType()
                        == ChatRoomType.OPEN
                        ? openChatMessageProfileService.resolveMap(
                        triggerMessage.getChatRoom().getId(),
                        userIds
                )
                        : Map.of();

        return new SenderContext(aliases, openProfiles);
    }

    private ChatAiReplyRequestDto.ContextMessage toContextMessage(
            ChatMessage message,
            SenderContext senderContext
    ) {
        String senderId = null;
        String senderName = null;

        if (message.getSenderType() == ChatMessageSenderType.USER
                && message.getSenderUser() != null) {
            senderId = senderContext.userAlias(message);
            senderName = senderContext.userName(message);
        } else if (message.getSenderType() == ChatMessageSenderType.AI
                && message.getSenderAiMember() != null
                && message.getSenderAiMember().getAiAgent() != null) {
            senderId = "ai-" + message.getSenderAiMember().getId();
            senderName = message.getSenderAiMember()
                    .getAiAgent()
                    .getNickname();
        }

        return new ChatAiReplyRequestDto.ContextMessage(
                message.getId(),
                message.getSenderType(),
                senderId,
                senderName,
                truncate(
                        message.getContent(),
                        AI_SERVER_MESSAGE_MAX_CHARACTERS
                ),
                message.getCreatedAt()
        );
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String buildRequestId(
            ChatAiTriggerType triggerType,
            Long messageId,
            Long aiMemberId
    ) {
        return "chat-ai:"
                + triggerType.name().toLowerCase()
                + ":"
                + messageId
                + ":"
                + aiMemberId;
    }

    private record SenderContext(
            Map<Long, String> aliases,
            Map<Long, OpenChatMessageSenderResponseDto> openProfiles
    ) {
        private String userAlias(ChatMessage message) {
            if (message == null || message.getSenderUser() == null) {
                return null;
            }
            return aliases.get(message.getSenderUser().getId());
        }

        private String userName(ChatMessage message) {
            if (message == null || message.getSenderUser() == null) {
                return null;
            }
            Long userId = message.getSenderUser().getId();
            OpenChatMessageSenderResponseDto openProfile =
                    openProfiles.get(userId);
            if (openProfile != null
                    && openProfile.nickname() != null
                    && !openProfile.nickname().isBlank()) {
                return truncate(
                        openProfile.nickname(),
                        AI_SERVER_SENDER_NAME_MAX_CHARACTERS
                );
            }
            String username = message.getSenderUser().getUsername();
            return username == null || username.isBlank()
                    ? "Member"
                    : truncate(
                            username,
                            AI_SERVER_SENDER_NAME_MAX_CHARACTERS
                    );
        }
    }
}
