package jp.co.translacat.domain.chat.message.service;

import jp.co.translacat.domain.chat.member.service.ChatRoomMemberQueryService;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageListResponseDto;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageResponseDto;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageTranslationResponseDto;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.translation.entity.ChatMessageTranslation;
import jp.co.translacat.domain.chat.translation.repository.ChatMessageTranslationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageQueryService {

    private static final int MESSAGE_PAGE_SIZE = 100;

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageTranslationRepository chatMessageTranslationRepository;
    private final ChatRoomMemberQueryService chatRoomMemberQueryService;

    public ChatMessageListResponseDto getMessages(
            Long loginUserId,
            Long chatRoomId,
            Long cursorId
    ) {
        validateChatRoomMember(loginUserId, chatRoomId);

        List<ChatMessage> fetchedMessages = fetchMessages(
                chatRoomId,
                cursorId
        );

        boolean hasNext = fetchedMessages.size() > MESSAGE_PAGE_SIZE;

        List<ChatMessage> pageMessages = trimToPageSize(fetchedMessages);

        /*
         * Repository에서는 최신순 DESC로 가져오고,
         * 응답은 화면 표시를 위해 오래된 메시지 → 최신 메시지 ASC 순서로 반환한다.
         */
        Collections.reverse(pageMessages);

        Map<Long, List<ChatMessageTranslationResponseDto>> translationMap =
                getTranslationMap(pageMessages);

        List<ChatMessageResponseDto> messages = pageMessages.stream()
                .map(message -> ChatMessageResponseDto.from(
                        message,
                        translationMap.getOrDefault(
                                message.getId(),
                                List.of()
                        )
                ))
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

    private void validateChatRoomMember(
            Long loginUserId,
            Long chatRoomId
    ) {
        chatRoomMemberQueryService.getActiveMember(
                loginUserId,
                chatRoomId
        );
    }

    private List<ChatMessage> fetchMessages(
            Long chatRoomId,
            Long cursorId
    ) {
        if (cursorId == null) {
            return chatMessageRepository
                    .findTop101ByChatRoomIdAndStatusAndDeletedAtIsNullOrderByIdDesc(
                            chatRoomId,
                            ChatMessageStatus.SENT
                    );
        }

        return chatMessageRepository
                .findTop101ByChatRoomIdAndStatusAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                        chatRoomId,
                        ChatMessageStatus.SENT,
                        cursorId
                );
    }

    private List<ChatMessage> trimToPageSize(List<ChatMessage> fetchedMessages) {
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

    private Map<Long, List<ChatMessageTranslationResponseDto>> getTranslationMap(
            List<ChatMessage> messages
    ) {
        if (messages.isEmpty()) {
            return Map.of();
        }

        List<Long> messageIds = messages.stream()
                .map(ChatMessage::getId)
                .toList();

        List<ChatMessageTranslation> translations =
                chatMessageTranslationRepository
                        .findByChatMessageIdInAndDeletedAtIsNull(messageIds);

        return translations.stream()
                .collect(Collectors.groupingBy(
                        translation -> translation.getChatMessage().getId(),
                        Collectors.mapping(
                                ChatMessageTranslationResponseDto::from,
                                Collectors.toList()
                        )
                ));
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