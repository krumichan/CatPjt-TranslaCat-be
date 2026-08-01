package jp.co.translacat.domain.chat.read.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.openchat.service.OpenChatAccessService;
import jp.co.translacat.domain.chat.openchat.support.OpenChatErrorCode;
import jp.co.translacat.domain.chat.read.dto.request.ChatRoomReadRequestDto;
import jp.co.translacat.domain.chat.read.dto.response.ChatRoomReadResponseDto;
import jp.co.translacat.domain.chat.read.event.ChatMemberReadUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.read.event.ChatReadUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.read.repository.ChatUnreadCountRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomReadServiceTest {

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatUnreadCountRepository chatUnreadCountRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private OpenChatAccessService openChatAccessService;

    private ChatRoomReadService chatRoomReadService;

    private User loginUser;
    private User sender;
    private ChatRoom chatRoom;
    private ChatRoomMember member;
    private LocalDateTime joinedAt;

    @BeforeEach
    void setUp() {
        chatRoomReadService = new ChatRoomReadService(
                chatRoomMemberRepository,
                chatMessageRepository,
                chatUnreadCountRepository,
                applicationEventPublisher,
                openChatAccessService
        );

        loginUser = createUser(
                1L,
                "reader@translacat.test",
                "reader",
                "READER0001"
        );
        sender = createUser(
                2L,
                "sender@translacat.test",
                "sender",
                "SENDER0001"
        );
        chatRoom = ChatRoom.createGroupRoom(
                "test",
                null,
                loginUser,
                ChatRoomSourceType.MANUAL
        );
        ReflectionTestUtils.setField(chatRoom, "id", 10L);

        member = ChatRoomMember.createMember(
                chatRoom,
                loginUser,
                "ko",
                "ja"
        );
        joinedAt = LocalDateTime.now().minusMinutes(10);
        ReflectionTestUtils.setField(member, "joinedAt", joinedAt);
    }

    @Test
    void advancesReadCursorAndPublishesUserAndRoomEvents() {
        ChatMessage message = createMessage(
                100L,
                joinedAt.plusMinutes(1)
        );
        when(chatRoomMemberRepository
                .findActiveByRoomIdAndUserIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(member));
        when(chatMessageRepository
                .findByIdAndChatRoomIdAndDeletedAtIsNull(100L, 10L))
                .thenReturn(Optional.of(message));
        when(chatUnreadCountRepository.countUnread(1L, 10L))
                .thenReturn(0L);

        ChatRoomReadResponseDto response = chatRoomReadService.markAsRead(
                1L,
                10L,
                new ChatRoomReadRequestDto(100L)
        );

        assertThat(response.chatRoomId()).isEqualTo(10L);
        assertThat(response.lastReadMessageId()).isEqualTo(100L);
        assertThat(response.lastReadAt()).isNotNull();
        assertThat(response.unreadCount()).isZero();
        verify(chatRoomMemberRepository).saveAndFlush(member);

        ArgumentCaptor<Object> eventCaptor =
                ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher, times(2))
                .publishEvent(eventCaptor.capture());

        List<Object> events = eventCaptor.getAllValues();
        ChatReadUpdatedApplicationEvent userEvent = events.stream()
                .filter(ChatReadUpdatedApplicationEvent.class::isInstance)
                .map(ChatReadUpdatedApplicationEvent.class::cast)
                .findFirst()
                .orElseThrow();
        ChatMemberReadUpdatedApplicationEvent roomEvent = events.stream()
                .filter(ChatMemberReadUpdatedApplicationEvent.class::isInstance)
                .map(ChatMemberReadUpdatedApplicationEvent.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(userEvent.destinationUsername())
                .isEqualTo("reader@translacat.test");
        assertThat(userEvent.userId()).isEqualTo(1L);

        assertThat(roomEvent.chatRoomId()).isEqualTo(10L);
        assertThat(roomEvent.readerUserId()).isEqualTo(1L);
        assertThat(roomEvent.previousLastReadMessageId()).isNull();
        assertThat(roomEvent.lastReadMessageId()).isEqualTo(100L);
        assertThat(roomEvent.readAt()).isNotNull();
    }

    @Test
    void publishesRoomEventWithPreviousAndNewCursor() {
        member.initializeReadCursor(90L);
        ChatMessage message = createMessage(
                100L,
                joinedAt.plusMinutes(1)
        );
        when(chatRoomMemberRepository
                .findActiveByRoomIdAndUserIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(member));
        when(chatMessageRepository
                .findByIdAndChatRoomIdAndDeletedAtIsNull(100L, 10L))
                .thenReturn(Optional.of(message));
        when(chatUnreadCountRepository.countUnread(1L, 10L))
                .thenReturn(0L);

        chatRoomReadService.markAsRead(
                1L,
                10L,
                new ChatRoomReadRequestDto(100L)
        );

        ArgumentCaptor<Object> eventCaptor =
                ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher, times(2))
                .publishEvent(eventCaptor.capture());

        ChatMemberReadUpdatedApplicationEvent roomEvent =
                eventCaptor.getAllValues().stream()
                        .filter(ChatMemberReadUpdatedApplicationEvent.class::isInstance)
                        .map(ChatMemberReadUpdatedApplicationEvent.class::cast)
                        .findFirst()
                        .orElseThrow();

        assertThat(roomEvent.previousLastReadMessageId()).isEqualTo(90L);
        assertThat(roomEvent.lastReadMessageId()).isEqualTo(100L);
    }

    @Test
    void doesNotMoveReadCursorBackwardOrPublishRoomEvent() {
        member.initializeReadCursor(200L);
        ChatMessage olderMessage = createMessage(
                150L,
                joinedAt.plusMinutes(1)
        );
        when(chatRoomMemberRepository
                .findActiveByRoomIdAndUserIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(member));
        when(chatMessageRepository
                .findByIdAndChatRoomIdAndDeletedAtIsNull(150L, 10L))
                .thenReturn(Optional.of(olderMessage));
        when(chatUnreadCountRepository.countUnread(1L, 10L))
                .thenReturn(2L);

        ChatRoomReadResponseDto response = chatRoomReadService.markAsRead(
                1L,
                10L,
                new ChatRoomReadRequestDto(150L)
        );

        assertThat(response.lastReadMessageId()).isEqualTo(200L);
        assertThat(response.unreadCount()).isEqualTo(2L);
        verify(chatRoomMemberRepository, never())
                .saveAndFlush(any(ChatRoomMember.class));
        verify(applicationEventPublisher)
                .publishEvent(isA(ChatReadUpdatedApplicationEvent.class));
        verify(applicationEventPublisher, never())
                .publishEvent(isA(
                        ChatMemberReadUpdatedApplicationEvent.class
                ));
    }

    @Test
    void bannedOpenChatUserCannotMarkAsRead() {
        doThrow(new BusinessException(
                "banned",
                OpenChatErrorCode.BANNED
        )).when(openChatAccessService)
                .validateOpenRoomMemberAccess(1L, 10L);

        assertThatThrownBy(() -> chatRoomReadService.markAsRead(
                1L,
                10L,
                new ChatRoomReadRequestDto(100L)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(
                        ((BusinessException) error).getErrorCode()
                ).isEqualTo(OpenChatErrorCode.BANNED));

        verify(chatRoomMemberRepository, never())
                .findActiveByRoomIdAndUserIdForUpdate(10L, 1L);
    }

    @Test
    void rejectsMessageCreatedBeforeLatestJoinedAt() {
        ChatMessage inaccessibleMessage = createMessage(
                90L,
                joinedAt.minusSeconds(1)
        );
        when(chatRoomMemberRepository
                .findActiveByRoomIdAndUserIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(member));
        when(chatMessageRepository
                .findByIdAndChatRoomIdAndDeletedAtIsNull(90L, 10L))
                .thenReturn(Optional.of(inaccessibleMessage));

        assertThatThrownBy(() -> chatRoomReadService.markAsRead(
                1L,
                10L,
                new ChatRoomReadRequestDto(90L)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("현재 참여 시점 이전 메시지는 읽음 처리할 수 없습니다.");

        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    private ChatMessage createMessage(
            Long id,
            LocalDateTime createdAt
    ) {
        ChatMessage message = ChatMessage.createUserTextMessage(
                chatRoom,
                sender,
                "hello"
        );
        ReflectionTestUtils.setField(message, "id", id);
        ReflectionTestUtils.setField(message, "createdAt", createdAt);
        return message;
    }

    private User createUser(
            Long id,
            String email,
            String username,
            String publicId
    ) {
        User user = User.createLocalUser(
                email,
                "password",
                username,
                Role.USER,
                publicId
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
