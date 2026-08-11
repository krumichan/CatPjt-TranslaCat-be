package jp.co.translacat.domain.chat.message.service;

import jp.co.translacat.domain.chat.ai.service.ChatAiProfileImageUrlResolver;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.service.ChatRoomMemberQueryService;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageAnchorListResponseDto;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageListResponseDto;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageResponseDto;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageTranslationResponseDto;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.message.repository.projection.ChatMessageAnchorWindowQueryResult;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMessageSenderResponseDto;
import jp.co.translacat.domain.chat.openchat.profile.service.OpenChatMessageProfileService;
import jp.co.translacat.domain.chat.openchat.service.OpenChatAccessService;
import jp.co.translacat.domain.chat.read.repository.ChatMessageUnreadMemberCountRepository;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.chat.translation.entity.ChatMessageTranslation;
import jp.co.translacat.domain.chat.translation.repository.ChatMessageTranslationRepository;
import jp.co.translacat.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ChatMessageQueryService {

    private static final int MESSAGE_PAGE_SIZE = 100;
    private static final int DEFAULT_ANCHOR_BEFORE_SIZE = 5;
    private static final int DEFAULT_ANCHOR_AFTER_SIZE = 30;
    private static final int MAX_ANCHOR_SIDE_SIZE = 100;
    private static final int DEFAULT_FORWARD_PAGE_SIZE = 100;
    private static final int MAX_FORWARD_PAGE_SIZE = 100;

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageTranslationRepository chatMessageTranslationRepository;
    private final ChatRoomMemberQueryService chatRoomMemberQueryService;
    private final ChatMessageSenderProfileService chatMessageSenderProfileService;
    private final ChatMessageUnreadMemberCountRepository
            chatMessageUnreadMemberCountRepository;
    private final OpenChatMessageProfileService openChatMessageProfileService;
    private final OpenChatAccessService openChatAccessService;
    private final ChatAiProfileImageUrlResolver chatAiProfileImageUrlResolver;

    @Autowired
    public ChatMessageQueryService(
            ChatMessageRepository chatMessageRepository,
            ChatMessageTranslationRepository chatMessageTranslationRepository,
            ChatRoomMemberQueryService chatRoomMemberQueryService,
            ChatMessageSenderProfileService chatMessageSenderProfileService,
            ChatMessageUnreadMemberCountRepository
                    chatMessageUnreadMemberCountRepository,
            OpenChatMessageProfileService openChatMessageProfileService,
            OpenChatAccessService openChatAccessService,
            ChatAiProfileImageUrlResolver chatAiProfileImageUrlResolver
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatMessageTranslationRepository =
                chatMessageTranslationRepository;
        this.chatRoomMemberQueryService = chatRoomMemberQueryService;
        this.chatMessageSenderProfileService =
                chatMessageSenderProfileService;
        this.chatMessageUnreadMemberCountRepository =
                chatMessageUnreadMemberCountRepository;
        this.openChatMessageProfileService =
                openChatMessageProfileService;
        this.openChatAccessService = openChatAccessService;
        this.chatAiProfileImageUrlResolver = chatAiProfileImageUrlResolver;
    }

    /**
     * 기존 단위 테스트 및 직접 생성 호출부 호환용 생성자.
     */
    public ChatMessageQueryService(
            ChatMessageRepository chatMessageRepository,
            ChatMessageTranslationRepository chatMessageTranslationRepository,
            ChatRoomMemberQueryService chatRoomMemberQueryService,
            ChatMessageSenderProfileService chatMessageSenderProfileService,
            ChatMessageUnreadMemberCountRepository
                    chatMessageUnreadMemberCountRepository,
            OpenChatMessageProfileService openChatMessageProfileService,
            OpenChatAccessService openChatAccessService
    ) {
        this(
                chatMessageRepository,
                chatMessageTranslationRepository,
                chatRoomMemberQueryService,
                chatMessageSenderProfileService,
                chatMessageUnreadMemberCountRepository,
                openChatMessageProfileService,
                openChatAccessService,
                null
        );
    }

    /**
     * 기존 단위 테스트 및 직접 생성 호출부 호환용 생성자.
     */
    public ChatMessageQueryService(
            ChatMessageRepository chatMessageRepository,
            ChatMessageTranslationRepository chatMessageTranslationRepository,
            ChatRoomMemberQueryService chatRoomMemberQueryService,
            ChatMessageSenderProfileService chatMessageSenderProfileService,
            ChatMessageUnreadMemberCountRepository
                    chatMessageUnreadMemberCountRepository,
            OpenChatMessageProfileService openChatMessageProfileService
    ) {
        this(
                chatMessageRepository,
                chatMessageTranslationRepository,
                chatRoomMemberQueryService,
                chatMessageSenderProfileService,
                chatMessageUnreadMemberCountRepository,
                openChatMessageProfileService,
                null,
                null
        );
    }

    /**
     * 기존 단위 테스트 및 직접 생성 호출부 호환용 생성자.
     */
    public ChatMessageQueryService(
            ChatMessageRepository chatMessageRepository,
            ChatMessageTranslationRepository chatMessageTranslationRepository,
            ChatRoomMemberQueryService chatRoomMemberQueryService,
            ChatMessageSenderProfileService chatMessageSenderProfileService,
            ChatMessageUnreadMemberCountRepository
                    chatMessageUnreadMemberCountRepository
    ) {
        this(
                chatMessageRepository,
                chatMessageTranslationRepository,
                chatRoomMemberQueryService,
                chatMessageSenderProfileService,
                chatMessageUnreadMemberCountRepository,
                null,
                null,
                null
        );
    }

    /**
     * 기존 단위 테스트 및 직접 생성 호출부 호환용 생성자.
     */
    public ChatMessageQueryService(
            ChatMessageRepository chatMessageRepository,
            ChatMessageTranslationRepository chatMessageTranslationRepository,
            ChatRoomMemberQueryService chatRoomMemberQueryService,
            ChatMessageSenderProfileService chatMessageSenderProfileService
    ) {
        this(
                chatMessageRepository,
                chatMessageTranslationRepository,
                chatRoomMemberQueryService,
                chatMessageSenderProfileService,
                null,
                null,
                null,
                null
        );
    }

    public ChatMessageListResponseDto getMessages(
            Long loginUserId,
            Long chatRoomId,
            Long cursorId
    ) {
        validateCursorId(cursorId);
        validateOpenChatAccess(loginUserId, chatRoomId);

        ChatRoomMember currentMember =
                chatRoomMemberQueryService.getActiveMember(
                        loginUserId,
                        chatRoomId
                );

        List<ChatMessage> fetchedMessages = fetchMessages(
                chatRoomId,
                cursorId,
                currentMember.getJoinedAt()
        );

        boolean hasNext = fetchedMessages.size() > MESSAGE_PAGE_SIZE;
        List<ChatMessage> pageMessages = trimToPageSize(fetchedMessages);

        /*
         * Repository에서는 최신순 DESC로 가져오고,
         * 응답은 오래된 메시지 → 최신 메시지 ASC로 반환한다.
         */
        Collections.reverse(pageMessages);

        List<ChatMessageResponseDto> messages = toResponses(
                chatRoomId,
                currentMember,
                pageMessages
        );

        Long nextCursorId = resolveNextCursorId(
                pageMessages,
                hasNext
        );

        return ChatMessageListResponseDto.of(
                messages,
                nextCursorId,
                hasNext
        );
    }

    public ChatMessageAnchorListResponseDto getMessagesAroundAnchor(
            Long loginUserId,
            Long chatRoomId,
            Long anchorMessageId,
            Integer requestedBeforeSize,
            Integer requestedAfterSize
    ) {
        validateRequiredMessageId(
                anchorMessageId,
                "anchorMessageId"
        );
        int beforeSize = normalizeAnchorSideSize(
                requestedBeforeSize,
                DEFAULT_ANCHOR_BEFORE_SIZE,
                "beforeSize"
        );
        int afterSize = normalizeAnchorSideSize(
                requestedAfterSize,
                DEFAULT_ANCHOR_AFTER_SIZE,
                "afterSize"
        );

        validateOpenChatAccess(loginUserId, chatRoomId);
        ChatRoomMember currentMember =
                chatRoomMemberQueryService.getActiveMember(
                        loginUserId,
                        chatRoomId
                );
        validateAccessibleCursorMessage(
                currentMember,
                chatRoomId,
                anchorMessageId,
                "Anchor 메시지를 찾을 수 없거나 접근할 수 없습니다.",
                "CHAT_MESSAGE_ANCHOR_NOT_ACCESSIBLE"
        );

        ChatMessageAnchorWindowQueryResult window =
                chatMessageRepository.findAnchorWindowIds(
                        chatRoomId,
                        anchorMessageId,
                        currentMember.getJoinedAt(),
                        beforeSize,
                        afterSize
                );
        List<ChatMessage> pageMessages =
                findMessagesByIdsPreservingOrder(window.messageIds());

        return ChatMessageAnchorListResponseDto.of(
                toResponses(
                        chatRoomId,
                        currentMember,
                        pageMessages
                ),
                anchorMessageId,
                window.previousCursorId(),
                window.hasPrevious(),
                window.nextCursorId(),
                window.hasNext()
        );
    }

    public ChatMessageListResponseDto getMessagesAfter(
            Long loginUserId,
            Long chatRoomId,
            Long cursorId,
            Integer requestedSize
    ) {
        validateRequiredMessageId(cursorId, "cursorId");
        int size = normalizeForwardPageSize(requestedSize);

        validateOpenChatAccess(loginUserId, chatRoomId);
        ChatRoomMember currentMember =
                chatRoomMemberQueryService.getActiveMember(
                        loginUserId,
                        chatRoomId
                );
        validateAccessibleCursorMessage(
                currentMember,
                chatRoomId,
                cursorId,
                "이후 메시지 조회 기준점을 찾을 수 없거나 접근할 수 없습니다.",
                "CHAT_MESSAGE_FORWARD_CURSOR_NOT_ACCESSIBLE"
        );

        List<Long> fetchedIds = chatMessageRepository.findNextMessageIds(
                chatRoomId,
                cursorId,
                currentMember.getJoinedAt(),
                size + 1
        );
        boolean hasNext = fetchedIds.size() > size;
        List<Long> pageIds = fetchedIds.size() > size
                ? new ArrayList<>(fetchedIds.subList(0, size))
                : new ArrayList<>(fetchedIds);
        List<ChatMessage> pageMessages =
                findMessagesByIdsPreservingOrder(pageIds);

        Long nextCursorId = hasNext && !pageMessages.isEmpty()
                ? pageMessages.getLast().getId()
                : null;

        return ChatMessageListResponseDto.of(
                toResponses(
                        chatRoomId,
                        currentMember,
                        pageMessages
                ),
                nextCursorId,
                hasNext
        );
    }

    private List<ChatMessageResponseDto> toResponses(
            Long chatRoomId,
            ChatRoomMember currentMember,
            List<ChatMessage> pageMessages
    ) {
        if (pageMessages.isEmpty()) {
            return List.of();
        }

        Map<Long, List<ChatMessageTranslationResponseDto>> translationMap =
                getTranslationMap(pageMessages);
        Map<Long, Long> unreadMemberCountMap =
                getUnreadMemberCountMap(pageMessages);

        boolean openRoom = currentMember.getChatRoom().getRoomType()
                == ChatRoomType.OPEN;
        Map<Long, String> senderProfileImageUrlMap = openRoom
                ? Map.of()
                : getSenderProfileImageUrlMap(pageMessages);
        Map<Long, OpenChatMessageSenderResponseDto> openChatSenderMap =
                openRoom
                        ? getOpenChatSenderMap(
                                chatRoomId,
                                pageMessages
                        )
                        : Map.of();

        return pageMessages.stream()
                .map(message -> toResponse(
                        message,
                        openRoom,
                        senderProfileImageUrlMap,
                        openChatSenderMap,
                        translationMap.getOrDefault(
                                message.getId(),
                                List.of()
                        ),
                        resolveUnreadMemberCount(
                                message,
                                unreadMemberCountMap
                        )
                ))
                .toList();
    }

    private ChatMessageResponseDto toResponse(
            ChatMessage message,
            boolean openRoom,
            Map<Long, String> senderProfileImageUrlMap,
            Map<Long, OpenChatMessageSenderResponseDto> openChatSenderMap,
            List<ChatMessageTranslationResponseDto> translations,
            Long unreadMemberCount
    ) {
        if (message.isAiMessage()) {
            return ChatMessageResponseDto.fromAi(
                    message,
                    resolveAiSenderProfileImageUrl(message),
                    translations,
                    unreadMemberCount
            );
        }

        if (openRoom) {
            OpenChatMessageSenderResponseDto sender =
                    message.getSenderUser() == null
                            ? null
                            : openChatSenderMap.get(
                                    message.getSenderUser().getId()
                            );
            return ChatMessageResponseDto.fromOpenChat(
                    message,
                    sender,
                    translations,
                    unreadMemberCount
            );
        }

        return ChatMessageResponseDto.from(
                message,
                resolveSenderProfileImageUrl(
                        message,
                        senderProfileImageUrlMap
                ),
                translations,
                unreadMemberCount
        );
    }

    private void validateOpenChatAccess(
            Long loginUserId,
            Long chatRoomId
    ) {
        if (openChatAccessService != null) {
            openChatAccessService.validateOpenRoomMemberAccess(
                    loginUserId,
                    chatRoomId
            );
        }
    }

    private void validateCursorId(Long cursorId) {
        if (cursorId != null && cursorId <= 0) {
            throw new BusinessException(
                    "cursorId는 1 이상이어야 합니다."
            );
        }
    }

    private void validateRequiredMessageId(
            Long messageId,
            String fieldName
    ) {
        if (messageId == null || messageId <= 0) {
            throw new BusinessException(
                    fieldName + "는 1 이상이어야 합니다.",
                    "CHAT_MESSAGE_CURSOR_INVALID"
            );
        }
    }

    private int normalizeAnchorSideSize(
            Integer requestedSize,
            int defaultSize,
            String fieldName
    ) {
        if (requestedSize == null) {
            return defaultSize;
        }
        if (requestedSize < 0
                || requestedSize > MAX_ANCHOR_SIDE_SIZE) {
            throw new BusinessException(
                    fieldName + "는 0 이상 "
                            + MAX_ANCHOR_SIDE_SIZE
                            + " 이하여야 합니다.",
                    "CHAT_MESSAGE_ANCHOR_SIZE_INVALID"
            );
        }
        return requestedSize;
    }

    private int normalizeForwardPageSize(Integer requestedSize) {
        if (requestedSize == null) {
            return DEFAULT_FORWARD_PAGE_SIZE;
        }
        if (requestedSize <= 0
                || requestedSize > MAX_FORWARD_PAGE_SIZE) {
            throw new BusinessException(
                    "size는 1 이상 "
                            + MAX_FORWARD_PAGE_SIZE
                            + " 이하여야 합니다.",
                    "CHAT_MESSAGE_FORWARD_SIZE_INVALID"
            );
        }
        return requestedSize;
    }

    private void validateAccessibleCursorMessage(
            ChatRoomMember currentMember,
            Long chatRoomId,
            Long messageId,
            String errorMessage,
            String errorCode
    ) {
        ChatMessage message = chatMessageRepository
                .findByIdAndChatRoomIdAndDeletedAtIsNull(
                        messageId,
                        chatRoomId
                )
                .orElseThrow(() -> new BusinessException(
                        errorMessage,
                        errorCode
                ));

        if (!message.isSent()
                || message.getCreatedAt() == null
                || currentMember.getJoinedAt() == null
                || message.getCreatedAt().isBefore(
                        currentMember.getJoinedAt()
                )) {
            throw new BusinessException(
                    errorMessage,
                    errorCode
            );
        }
    }

    private List<ChatMessage> findMessagesByIdsPreservingOrder(
            List<Long> messageIds
    ) {
        if (messageIds == null || messageIds.isEmpty()) {
            return List.of();
        }

        Map<Long, ChatMessage> messageById = chatMessageRepository
                .findByIdInAndDeletedAtIsNull(messageIds)
                .stream()
                .filter(ChatMessage::isSent)
                .collect(Collectors.toMap(
                        ChatMessage::getId,
                        message -> message,
                        (left, right) -> left
                ));

        return messageIds.stream()
                .map(messageById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<ChatMessage> fetchMessages(
            Long chatRoomId,
            Long cursorId,
            LocalDateTime joinedAt
    ) {
        if (cursorId == null) {
            return chatMessageRepository
                    .findTop101ByChatRoomIdAndStatusAndDeletedAtIsNullAndCreatedAtGreaterThanEqualOrderByIdDesc(
                            chatRoomId,
                            ChatMessageStatus.SENT,
                            joinedAt
                    );
        }

        return chatMessageRepository
                .findTop101ByChatRoomIdAndStatusAndDeletedAtIsNullAndCreatedAtGreaterThanEqualAndIdLessThanOrderByIdDesc(
                        chatRoomId,
                        ChatMessageStatus.SENT,
                        joinedAt,
                        cursorId
                );
    }

    private List<ChatMessage> trimToPageSize(
            List<ChatMessage> fetchedMessages
    ) {
        if (fetchedMessages.size() <= MESSAGE_PAGE_SIZE) {
            return new ArrayList<>(fetchedMessages);
        }

        return new ArrayList<>(
                fetchedMessages.subList(0, MESSAGE_PAGE_SIZE)
        );
    }

    private Map<Long, List<ChatMessageTranslationResponseDto>>
    getTranslationMap(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return Map.of();
        }

        List<Long> messageIds = messages.stream()
                .map(ChatMessage::getId)
                .toList();

        List<ChatMessageTranslation> translations =
                chatMessageTranslationRepository
                        .findByChatMessageIdInAndDeletedAtIsNull(
                                messageIds
                        );

        return translations.stream()
                .collect(Collectors.groupingBy(
                        translation ->
                                translation.getChatMessage().getId(),
                        Collectors.mapping(
                                ChatMessageTranslationResponseDto::from,
                                Collectors.toList()
                        )
                ));
    }

    private Map<Long, String> getSenderProfileImageUrlMap(
            List<ChatMessage> messages
    ) {
        if (messages.isEmpty()
                || chatMessageSenderProfileService == null) {
            return Map.of();
        }

        Set<Long> senderUserIds = messages.stream()
                .map(ChatMessage::getSenderUser)
                .filter(Objects::nonNull)
                .map(user -> user.getId())
                .collect(Collectors.toSet());

        return chatMessageSenderProfileService
                .resolveLatestProfileImageUrlMap(senderUserIds);
    }

    private Map<Long, OpenChatMessageSenderResponseDto>
    getOpenChatSenderMap(
            Long roomId,
            List<ChatMessage> messages
    ) {
        if (messages.isEmpty()
                || openChatMessageProfileService == null) {
            return Map.of();
        }

        Set<Long> senderUserIds = messages.stream()
                .map(ChatMessage::getSenderUser)
                .filter(Objects::nonNull)
                .map(user -> user.getId())
                .collect(Collectors.toSet());

        return openChatMessageProfileService.resolveMap(
                roomId,
                senderUserIds
        );
    }

    private Map<Long, Long> getUnreadMemberCountMap(
            List<ChatMessage> messages
    ) {
        if (messages.isEmpty()
                || chatMessageUnreadMemberCountRepository == null) {
            return Map.of();
        }

        List<Long> messageIds = messages.stream()
                .filter(message -> !message.isSystemMessage())
                .map(ChatMessage::getId)
                .toList();

        return chatMessageUnreadMemberCountRepository
                .countUnreadMembersByMessageIds(messageIds);
    }

    private String resolveSenderProfileImageUrl(
            ChatMessage message,
            Map<Long, String> senderProfileImageUrlMap
    ) {
        if (message.getSenderUser() == null) {
            return null;
        }
        return senderProfileImageUrlMap.get(
                message.getSenderUser().getId()
        );
    }

    private Long resolveUnreadMemberCount(
            ChatMessage message,
            Map<Long, Long> unreadMemberCountMap
    ) {
        if (message.isSystemMessage()) {
            return null;
        }
        if (chatMessageUnreadMemberCountRepository == null) {
            return null;
        }
        return unreadMemberCountMap.getOrDefault(
                message.getId(),
                0L
        );
    }

    private Long resolveNextCursorId(
            List<ChatMessage> pageMessages,
            boolean hasNext
    ) {
        if (!hasNext || pageMessages.isEmpty()) {
            return null;
        }
        return pageMessages.getFirst().getId();
    }

    private String resolveAiSenderProfileImageUrl(
            ChatMessage message
    ) {
        if (chatAiProfileImageUrlResolver == null
                || message.getSenderAiMember() == null
                || message.getSenderAiMember().getAiAgent() == null) {
            return null;
        }
        return chatAiProfileImageUrlResolver.resolveProfileImageUrl(
                message.getSenderAiMember().getAiAgent()
        );
    }

}
