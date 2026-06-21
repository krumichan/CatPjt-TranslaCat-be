package jp.co.translacat.domain.chat.message.service;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.language.service.ChatLanguageSettingResolver;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.member.service.ChatRoomMemberQueryService;
import jp.co.translacat.domain.chat.message.dto.request.ChatMessageCreateRequestDto;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageResponseDto;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageTranslationResponseDto;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.translation.entity.ChatMessageTranslation;
import jp.co.translacat.domain.chat.translation.repository.ChatMessageTranslationRepository;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatMessageCommandService {

    private static final int MAX_MESSAGE_CONTENT_LENGTH = 5000;

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageTranslationRepository chatMessageTranslationRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatRoomMemberQueryService chatRoomMemberQueryService;
    private final ChatLanguageSettingResolver chatLanguageSettingResolver;

    public ChatMessageResponseDto createTextMessage(
            Long loginUserId,
            Long chatRoomId,
            ChatMessageCreateRequestDto request
    ) {
        validateCreateRequest(request);

        ChatRoomMember senderMember = chatRoomMemberQueryService.getActiveMember(
                loginUserId,
                chatRoomId
        );

        ChatMessage message = ChatMessage.createUserTextMessage(
                senderMember.getChatRoom(),
                senderMember.getUser(),
                normalizeContent(request.content())
        );

        ChatMessage savedMessage = chatMessageRepository.save(message);

        List<ChatMessageTranslation> translations =
                createPendingTranslations(
                        savedMessage,
                        senderMember
                );

        List<ChatMessageTranslationResponseDto> translationResponses =
                translations.stream()
                        .map(ChatMessageTranslationResponseDto::from)
                        .toList();

        return ChatMessageResponseDto.from(
                savedMessage,
                translationResponses
        );
    }

    private List<ChatMessageTranslation> createPendingTranslations(
            ChatMessage message,
            ChatRoomMember senderMember
    ) {
        List<ChatRoomMember> activeMembers = chatRoomMemberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNull(
                        message.getChatRoom().getId()
                );

        String senderOriginalLanguageCode =
                chatLanguageSettingResolver
                        .resolve(senderMember)
                        .originalLanguageCode();

        Set<String> targetLanguageCodes = new LinkedHashSet<>();

        for (ChatRoomMember member : activeMembers) {
            ChatLanguageSettingResult languageSetting =
                    chatLanguageSettingResolver.resolve(member);

            String targetLanguageCode = languageSetting.translationLanguageCode();

            if (isBlank(targetLanguageCode)) {
                continue;
            }

            if (targetLanguageCode.equalsIgnoreCase(senderOriginalLanguageCode)) {
                continue;
            }

            targetLanguageCodes.add(targetLanguageCode.trim().toLowerCase());
        }

        List<ChatMessageTranslation> translations = targetLanguageCodes.stream()
                .map(languageCode -> ChatMessageTranslation.createPending(
                        message,
                        languageCode
                ))
                .toList();

        return chatMessageTranslationRepository.saveAll(translations);
    }

    private void validateCreateRequest(ChatMessageCreateRequestDto request) {
        if (request == null || isBlank(request.content())) {
            throw new BusinessException("메시지 내용은 필수입니다.");
        }

        if (request.content().trim().length() > MAX_MESSAGE_CONTENT_LENGTH) {
            throw new BusinessException("메시지는 5000자 이하로 입력해주세요.");
        }
    }

    private String normalizeContent(String content) {
        return content.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}