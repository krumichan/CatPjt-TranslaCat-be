package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiMemberRepository;
import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.language.service.ChatLanguageSettingResolver;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageResponseDto;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageTranslationResponseDto;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.read.repository.ChatMessageUnreadMemberCountRepository;
import jp.co.translacat.domain.chat.translation.entity.ChatMessageTranslation;
import jp.co.translacat.domain.chat.translation.event.ChatMessageTranslationRequestedEvent;
import jp.co.translacat.domain.chat.translation.repository.ChatMessageTranslationRepository;
import jp.co.translacat.domain.chat.websocket.service.ChatWebSocketEventPublisher;
import jp.co.translacat.global.utils.ValidationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChatAiMessageCommandService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomAiMemberRepository chatRoomAiMemberRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageTranslationRepository translationRepository;
    private final ChatLanguageSettingResolver languageSettingResolver;
    private final ChatMessageUnreadMemberCountRepository unreadMemberCountRepository;
    private final ChatAiProfileImageUrlResolver profileImageUrlResolver;
    private final ChatWebSocketEventPublisher webSocketEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatMessageResponseDto createAiTextMessage(
            Long roomId,
            Long aiMemberId,
            String requestId,
            String reply
    ) {
        if (requestId == null || requestId.isBlank()
                || reply == null || reply.isBlank()) {
            return null;
        }

        if (chatMessageRepository.existsByAiRequestId(requestId)) {
            return null;
        }

        ChatRoomAiMember aiMember = chatRoomAiMemberRepository
                .findByIdAndChatRoomIdAndActiveTrueAndDeletedAtIsNull(
                        aiMemberId,
                        roomId
                )
                .orElse(null);
        if (aiMember == null
                || aiMember.getAiAgent() == null
                || !aiMember.getAiAgent().isActive()
                || aiMember.getAiAgent().isDeleted()) {
            return null;
        }

        ChatMessage message = ChatMessage.createAiTextMessage(
                aiMember.getChatRoom(),
                aiMember,
                reply.trim(),
                requestId
        );
        ChatMessage savedMessage = chatMessageRepository.saveAndFlush(message);

        List<ChatMessageTranslation> translations =
                createPendingTranslations(savedMessage, aiMember);
        List<ChatMessageTranslationResponseDto> translationResponses =
                translations.stream()
                        .map(ChatMessageTranslationResponseDto::from)
                        .toList();

        Long unreadMemberCount = unreadMemberCountRepository
                .countUnreadMembers(savedMessage.getId());

        ChatMessageResponseDto response = ChatMessageResponseDto.fromAi(
                savedMessage,
                profileImageUrlResolver.resolveProfileImageUrl(
                        aiMember.getAiAgent()
                ),
                translationResponses,
                unreadMemberCount
        );

        webSocketEventPublisher.publishMessageCreated(roomId, response);
        publishTranslationRequestedEvent(savedMessage, translations);
        return response;
    }

    private List<ChatMessageTranslation> createPendingTranslations(
            ChatMessage message,
            ChatRoomAiMember aiMember
    ) {
        List<ChatRoomMember> activeMembers = chatRoomMemberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNull(
                        message.getChatRoom().getId()
                );

        String originalLanguageCode = aiMember.getAiAgent()
                .getOriginalLanguageCode();
        Set<String> targetLanguageCodes = new LinkedHashSet<>();

        for (ChatRoomMember member : activeMembers) {
            ChatLanguageSettingResult languageSetting =
                    languageSettingResolver.resolve(member);
            String targetLanguageCode =
                    languageSetting.translationLanguageCode();

            if (ValidationUtil.isBlank(targetLanguageCode)) {
                continue;
            }
            if (targetLanguageCode.equalsIgnoreCase(
                    originalLanguageCode
            )) {
                continue;
            }
            targetLanguageCodes.add(
                    targetLanguageCode.trim().toLowerCase()
            );
        }

        List<ChatMessageTranslation> translations =
                targetLanguageCodes.stream()
                        .map(languageCode ->
                                ChatMessageTranslation.createPending(
                                        message,
                                        languageCode
                                )
                        )
                        .toList();
        return translationRepository.saveAll(translations);
    }

    private void publishTranslationRequestedEvent(
            ChatMessage message,
            List<ChatMessageTranslation> translations
    ) {
        if (translations.isEmpty()) {
            return;
        }

        applicationEventPublisher.publishEvent(
                ChatMessageTranslationRequestedEvent.of(
                        message.getChatRoom().getId(),
                        message.getId(),
                        null,
                        translations.stream()
                                .map(ChatMessageTranslation::getId)
                                .toList()
                )
        );
    }
}
