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
import jp.co.translacat.domain.chat.read.repository.ChatMessageUnreadMemberCountRepository;
import jp.co.translacat.domain.chat.translation.entity.ChatMessageTranslation;
import jp.co.translacat.domain.chat.translation.event.ChatMessageTranslationRequestedEvent;
import jp.co.translacat.domain.chat.translation.repository.ChatMessageTranslationRepository;
import jp.co.translacat.domain.chat.websocket.service.ChatWebSocketEventPublisher;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.utils.ValidationUtil;
import jp.co.translacat.global.utils.ValueUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class ChatMessageCommandService {

    private static final int MAX_MESSAGE_CONTENT_LENGTH = 5000;

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageTranslationRepository chatMessageTranslationRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatRoomMemberQueryService chatRoomMemberQueryService;
    private final ChatLanguageSettingResolver chatLanguageSettingResolver;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ChatWebSocketEventPublisher chatWebSocketEventPublisher;
    private final ChatMessageSenderProfileService chatMessageSenderProfileService;
    private final ChatMessageUnreadMemberCountRepository
            chatMessageUnreadMemberCountRepository;

    @Autowired
    public ChatMessageCommandService(
            ChatMessageRepository chatMessageRepository,
            ChatMessageTranslationRepository chatMessageTranslationRepository,
            ChatRoomMemberRepository chatRoomMemberRepository,
            ChatRoomMemberQueryService chatRoomMemberQueryService,
            ChatLanguageSettingResolver chatLanguageSettingResolver,
            ApplicationEventPublisher applicationEventPublisher,
            ChatWebSocketEventPublisher chatWebSocketEventPublisher,
            ChatMessageSenderProfileService chatMessageSenderProfileService,
            ChatMessageUnreadMemberCountRepository
                    chatMessageUnreadMemberCountRepository
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatMessageTranslationRepository =
                chatMessageTranslationRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.chatRoomMemberQueryService = chatRoomMemberQueryService;
        this.chatLanguageSettingResolver = chatLanguageSettingResolver;
        this.applicationEventPublisher = applicationEventPublisher;
        this.chatWebSocketEventPublisher = chatWebSocketEventPublisher;
        this.chatMessageSenderProfileService =
                chatMessageSenderProfileService;
        this.chatMessageUnreadMemberCountRepository =
                chatMessageUnreadMemberCountRepository;
    }

    /**
     * 기존 단위 테스트 및 직접 생성 호출부 호환용 생성자.
     */
    public ChatMessageCommandService(
            ChatMessageRepository chatMessageRepository,
            ChatMessageTranslationRepository chatMessageTranslationRepository,
            ChatRoomMemberRepository chatRoomMemberRepository,
            ChatRoomMemberQueryService chatRoomMemberQueryService,
            ChatLanguageSettingResolver chatLanguageSettingResolver,
            ApplicationEventPublisher applicationEventPublisher,
            ChatWebSocketEventPublisher chatWebSocketEventPublisher,
            ChatMessageSenderProfileService chatMessageSenderProfileService
    ) {
        this(
                chatMessageRepository,
                chatMessageTranslationRepository,
                chatRoomMemberRepository,
                chatRoomMemberQueryService,
                chatLanguageSettingResolver,
                applicationEventPublisher,
                chatWebSocketEventPublisher,
                chatMessageSenderProfileService,
                null
        );
    }

    public ChatMessageResponseDto createTextMessage(
            Long loginUserId,
            Long chatRoomId,
            ChatMessageCreateRequestDto request
    ) {
        validateCreateRequest(request);

        ChatRoomMember senderMember =
                chatRoomMemberQueryService.getActiveMember(
                        loginUserId,
                        chatRoomId
                );

        ChatMessage message = ChatMessage.createUserTextMessage(
                senderMember.getChatRoom(),
                senderMember.getUser(),
                ValueUtil.normalizeContent(request.content())
        );

        ChatMessage savedMessage =
                chatMessageRepository.save(message);

        List<ChatMessageTranslation> translations =
                createPendingTranslations(
                        savedMessage,
                        senderMember
                );

        List<ChatMessageTranslationResponseDto> translationResponses =
                translations.stream()
                        .map(ChatMessageTranslationResponseDto::from)
                        .toList();

        String senderProfileImageUrl =
                resolveSenderProfileImageUrl(
                        senderMember.getUser().getId()
                );

        Long unreadMemberCount =
                resolveInitialUnreadMemberCount(savedMessage);

        ChatMessageResponseDto response =
                ChatMessageResponseDto.from(
                        savedMessage,
                        senderProfileImageUrl,
                        translationResponses,
                        unreadMemberCount
                );

        /*
         * REST 생성과 WebSocket SEND 생성 모두 이 Service를 통과한다.
         * 따라서 message.created publish도 여기에서 일원화한다.
         */
        chatWebSocketEventPublisher.publishMessageCreated(
                savedMessage.getChatRoom().getId(),
                response
        );

        publishTranslationRequestedEvent(
                savedMessage,
                senderMember,
                translations
        );

        return response;
    }

    private List<ChatMessageTranslation> createPendingTranslations(
            ChatMessage message,
            ChatRoomMember senderMember
    ) {
        List<ChatRoomMember> activeMembers =
                chatRoomMemberRepository
                        .findByChatRoomIdAndActiveTrueAndDeletedAtIsNull(
                                message.getChatRoom().getId()
                        );

        String senderOriginalLanguageCode =
                chatLanguageSettingResolver
                        .resolve(senderMember)
                        .originalLanguageCode();

        Set<String> targetLanguageCodes =
                new LinkedHashSet<>();

        for (ChatRoomMember member : activeMembers) {
            ChatLanguageSettingResult languageSetting =
                    chatLanguageSettingResolver.resolve(member);

            String targetLanguageCode =
                    languageSetting.translationLanguageCode();

            if (ValidationUtil.isBlank(targetLanguageCode)) {
                continue;
            }

            if (targetLanguageCode.equalsIgnoreCase(
                    senderOriginalLanguageCode
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

        return chatMessageTranslationRepository.saveAll(
                translations
        );
    }

    private String resolveSenderProfileImageUrl(Long senderUserId) {
        /*
         * 기존 Mockito 테스트에서 신규 의존성을 아직 mock하지 않아도
         * NPE가 발생하지 않도록 null fallback을 둔다.
         */
        if (chatMessageSenderProfileService == null
                || senderUserId == null) {
            return null;
        }

        return chatMessageSenderProfileService
                .resolveLatestProfileImageUrl(senderUserId);
    }

    private Long resolveInitialUnreadMemberCount(
            ChatMessage message
    ) {
        /*
         * 기존 단위 테스트가 호환 생성자를 사용하는 경우에는
         * 신규 Repository가 null일 수 있다. 실제 Spring Context에서는
         * 반드시 주입되며, 생성 시점의 서버 기준 미확인 인원 수를
         * message.created Payload에 포함한다.
         */
        if (chatMessageUnreadMemberCountRepository == null
                || message == null
                || message.isSystemMessage()) {
            return null;
        }
        return chatMessageUnreadMemberCountRepository
                .countUnreadMembers(message.getId());
    }

    private void validateCreateRequest(
            ChatMessageCreateRequestDto request
    ) {
        if (request == null
                || ValidationUtil.isBlank(request.content())) {
            throw new BusinessException(
                    "메시지 내용은 필수입니다."
            );
        }

        if (request.content().trim().length()
                > MAX_MESSAGE_CONTENT_LENGTH) {
            throw new BusinessException(
                    "메시지는 5000자 이하로 입력해주세요."
            );
        }
    }

    private void publishTranslationRequestedEvent(
            ChatMessage message,
            ChatRoomMember senderMember,
            List<ChatMessageTranslation> translations
    ) {
        if (translations.isEmpty()) {
            return;
        }

        List<Long> translationIds = translations.stream()
                .map(ChatMessageTranslation::getId)
                .toList();

        applicationEventPublisher.publishEvent(
                ChatMessageTranslationRequestedEvent.of(
                        message.getChatRoom().getId(),
                        message.getId(),
                        senderMember.getUser().getId(),
                        translationIds
                )
        );
    }
}
