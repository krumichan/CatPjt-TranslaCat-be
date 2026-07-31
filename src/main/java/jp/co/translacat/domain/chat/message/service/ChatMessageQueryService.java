package jp.co.translacat.domain.chat.message.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.service.ChatRoomMemberQueryService;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageListResponseDto;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageResponseDto;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageTranslationResponseDto;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.read.repository.ChatMessageUnreadMemberCountRepository;
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

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageTranslationRepository chatMessageTranslationRepository;
    private final ChatRoomMemberQueryService chatRoomMemberQueryService;
    private final ChatMessageSenderProfileService chatMessageSenderProfileService;
    private final ChatMessageUnreadMemberCountRepository
            chatMessageUnreadMemberCountRepository;

    @Autowired
    public ChatMessageQueryService(
            ChatMessageRepository chatMessageRepository,
            ChatMessageTranslationRepository chatMessageTranslationRepository,
            ChatRoomMemberQueryService chatRoomMemberQueryService,
            ChatMessageSenderProfileService chatMessageSenderProfileService,
            ChatMessageUnreadMemberCountRepository
                    chatMessageUnreadMemberCountRepository
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatMessageTranslationRepository =
                chatMessageTranslationRepository;
        this.chatRoomMemberQueryService = chatRoomMemberQueryService;
        this.chatMessageSenderProfileService =
                chatMessageSenderProfileService;
        this.chatMessageUnreadMemberCountRepository =
                chatMessageUnreadMemberCountRepository;
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
                null
        );
    }

    public ChatMessageListResponseDto getMessages(
            Long loginUserId,
            Long chatRoomId,
            Long cursorId
    ) {
        validateCursorId(cursorId);

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

        boolean hasNext =
                fetchedMessages.size() > MESSAGE_PAGE_SIZE;

        List<ChatMessage> pageMessages =
                trimToPageSize(fetchedMessages);

        /*
         * Repository에서는 최신순 DESC로 가져오고,
         * 응답은 오래된 메시지 → 최신 메시지 ASC로 반환한다.
         */
        Collections.reverse(pageMessages);

        Map<Long, List<ChatMessageTranslationResponseDto>>
                translationMap =
                getTranslationMap(pageMessages);

        Map<Long, String> senderProfileImageUrlMap =
                getSenderProfileImageUrlMap(pageMessages);

        Map<Long, Long> unreadMemberCountMap =
                getUnreadMemberCountMap(pageMessages);

        List<ChatMessageResponseDto> messages =
                pageMessages.stream()
                        .map(message ->
                                ChatMessageResponseDto.from(
                                        message,
                                        resolveSenderProfileImageUrl(
                                                message,
                                                senderProfileImageUrlMap
                                        ),
                                        translationMap.getOrDefault(
                                                message.getId(),
                                                List.of()
                                        ),
                                        resolveUnreadMemberCount(
                                                message,
                                                unreadMemberCountMap
                                        )
                                )
                        )
                        .toList();

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

    private void validateCursorId(Long cursorId) {
        if (cursorId != null && cursorId <= 0) {
            throw new BusinessException(
                    "cursorId는 1 이상이어야 합니다."
            );
        }
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
                fetchedMessages.subList(
                        0,
                        MESSAGE_PAGE_SIZE
                )
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
                .resolveLatestProfileImageUrlMap(
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
}
