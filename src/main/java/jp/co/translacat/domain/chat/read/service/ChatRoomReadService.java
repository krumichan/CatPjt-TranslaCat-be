package jp.co.translacat.domain.chat.read.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.read.dto.request.ChatRoomReadRequestDto;
import jp.co.translacat.domain.chat.read.dto.response.ChatRoomReadResponseDto;
import jp.co.translacat.domain.chat.read.event.ChatMemberReadUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.read.event.ChatReadUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.read.repository.ChatUnreadCountRepository;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatRoomReadService {

    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatUnreadCountRepository chatUnreadCountRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ChatRoomReadResponseDto markAsRead(
            Long loginUserId,
            Long chatRoomId,
            ChatRoomReadRequestDto request
    ) {
        if (chatRoomId == null) {
            throw new BusinessException(
                    "채팅방 ID는 필수입니다.",
                    "CHAT_ROOM_ID_REQUIRED"
            );
        }
        if (request == null || request.lastReadMessageId() == null) {
            throw new BusinessException(
                    "마지막 읽은 메시지 ID는 필수입니다.",
                    "CHAT_ROOM_LAST_READ_MESSAGE_ID_REQUIRED"
            );
        }

        ChatRoomMember member = chatRoomMemberRepository
                .findActiveByRoomIdAndUserIdForUpdate(
                        chatRoomId,
                        loginUserId
                )
                .orElseThrow(() -> new BusinessException(
                        "채팅방 멤버가 아니거나 접근 권한이 없습니다.",
                        "CHAT_ROOM_MEMBER_ACCESS_DENIED"
                ));

        ChatMessage message = chatMessageRepository
                .findByIdAndChatRoomIdAndDeletedAtIsNull(
                        request.lastReadMessageId(),
                        chatRoomId
                )
                .orElseThrow(() -> new BusinessException(
                        "읽음 처리할 메시지를 찾을 수 없습니다.",
                        "CHAT_ROOM_READ_MESSAGE_NOT_FOUND"
                ));

        validateReadableMessage(member, message);

        Long previousLastReadMessageId =
                member.getLastReadMessageId();
        boolean advanced = member.advanceReadCursor(message.getId());
        if (advanced) {
            chatRoomMemberRepository.saveAndFlush(member);
        }

        long unreadCount = chatUnreadCountRepository.countUnread(
                loginUserId,
                chatRoomId
        );
        ChatRoomReadResponseDto response =
                ChatRoomReadResponseDto.from(member, unreadCount);

        publishUserReadUpdatedEvent(
                loginUserId,
                member,
                response
        );

        if (advanced) {
            publishMemberReadUpdatedEvent(
                    loginUserId,
                    chatRoomId,
                    previousLastReadMessageId,
                    response
            );
        }

        return response;
    }

    private void publishUserReadUpdatedEvent(
            Long loginUserId,
            ChatRoomMember member,
            ChatRoomReadResponseDto response
    ) {
        applicationEventPublisher.publishEvent(
                ChatReadUpdatedApplicationEvent.of(
                        member.getUser().getEmail(),
                        loginUserId,
                        response
                )
        );
    }

    private void publishMemberReadUpdatedEvent(
            Long loginUserId,
            Long chatRoomId,
            Long previousLastReadMessageId,
            ChatRoomReadResponseDto response
    ) {
        applicationEventPublisher.publishEvent(
                ChatMemberReadUpdatedApplicationEvent.of(
                        chatRoomId,
                        loginUserId,
                        previousLastReadMessageId,
                        response.lastReadMessageId(),
                        response.lastReadAt()
                )
        );
    }

    private void validateReadableMessage(
            ChatRoomMember member,
            ChatMessage message
    ) {
        if (!message.isSent()) {
            throw new BusinessException(
                    "전송 완료된 메시지만 읽음 처리할 수 있습니다.",
                    "CHAT_ROOM_READ_MESSAGE_NOT_ACCESSIBLE"
            );
        }
        if (message.getCreatedAt() == null
                || member.getJoinedAt() == null
                || message.getCreatedAt().isBefore(member.getJoinedAt())) {
            throw new BusinessException(
                    "현재 참여 시점 이전 메시지는 읽음 처리할 수 없습니다.",
                    "CHAT_ROOM_READ_MESSAGE_NOT_ACCESSIBLE"
            );
        }
    }
}
