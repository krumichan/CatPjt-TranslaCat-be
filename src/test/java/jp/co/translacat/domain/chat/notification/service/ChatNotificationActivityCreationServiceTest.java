package jp.co.translacat.domain.chat.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.event.ChatRoomMemberInvitedApplicationEvent;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.notification.entity.ChatNotification;
import jp.co.translacat.domain.chat.notification.enums.ChatNotificationType;
import jp.co.translacat.domain.chat.notification.event.ChatNotificationCreatedApplicationEvent;
import jp.co.translacat.domain.chat.notification.repository.ChatNotificationRepository;
import jp.co.translacat.domain.chat.openchat.event.OpenChatMemberBannedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.event.OpenChatMemberRoleUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.event.OpenChatRoomClosedApplicationEvent;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatNotificationActivityCreationServiceTest {

    @Mock
    private ChatNotificationRepository notificationRepository;
    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatRoomMemberRepository memberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ChatNotificationActivityCreationService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        service = new ChatNotificationActivityCreationService(
                notificationRepository,
                chatRoomRepository,
                memberRepository,
                userRepository,
                objectMapper,
                new ChatNotificationActivityResponseMapper(objectMapper),
                eventPublisher
        );
    }

    @Test
    void createsGroupInvitationAndPublishesCreatedEvent() {
        stubSaveAndFlush();
        User recipient = user(2L, "recipient");
        User actor = user(1L, "actor");
        ChatRoom room = groupRoom(100L, actor, "group-room");
        LocalDateTime joinedAt = LocalDateTime.of(2026, 8, 11, 14, 0);
        ChatRoomMemberInvitedApplicationEvent sourceEvent =
                ChatRoomMemberInvitedApplicationEvent.of(
                        100L,
                        2L,
                        1L,
                        20L,
                        joinedAt
                );

        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(chatRoomRepository.findById(100L)).thenReturn(Optional.of(room));
        when(notificationRepository
                .existsByRecipientUser_IdAndNotificationTypeAndSourceEventKey(
                        2L,
                        ChatNotificationType.CHAT_INVITATION,
                        sourceEvent.sourceEventKey()
                )).thenReturn(false);

        service.createInvitation(sourceEvent);

        ArgumentCaptor<ChatNotification> notificationCaptor =
                ArgumentCaptor.forClass(ChatNotification.class);
        verify(notificationRepository).saveAndFlush(
                notificationCaptor.capture()
        );
        ChatNotification saved = notificationCaptor.getValue();
        assertThat(saved.getNotificationType())
                .isEqualTo(ChatNotificationType.CHAT_INVITATION);
        assertThat(saved.getRecipientUser().getId()).isEqualTo(2L);
        assertThat(saved.getActorUser().getId()).isEqualTo(1L);
        assertThat(saved.getPayloadJson()).contains("group-room");

        ArgumentCaptor<ChatNotificationCreatedApplicationEvent> eventCaptor =
                ArgumentCaptor.forClass(
                        ChatNotificationCreatedApplicationEvent.class
                );
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().destinationUsername())
                .isEqualTo(recipient.getEmail());
        assertThat(eventCaptor.getValue().notification().notificationType())
                .isEqualTo(ChatNotificationType.CHAT_INVITATION);
    }

    @Test
    void skipsDuplicateSourceEventWithoutWebSocketEvent() {
        User recipient = user(2L, "recipient");
        ChatRoom room = groupRoom(100L, user(1L, "actor"), "group-room");
        ChatRoomMemberInvitedApplicationEvent sourceEvent =
                ChatRoomMemberInvitedApplicationEvent.of(
                        100L,
                        2L,
                        1L,
                        20L,
                        LocalDateTime.of(2026, 8, 11, 14, 0)
                );

        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "actor")));
        when(chatRoomRepository.findById(100L)).thenReturn(Optional.of(room));
        when(notificationRepository
                .existsByRecipientUser_IdAndNotificationTypeAndSourceEventKey(
                        2L,
                        ChatNotificationType.CHAT_INVITATION,
                        sourceEvent.sourceEventKey()
                )).thenReturn(true);

        service.createInvitation(sourceEvent);

        verify(notificationRepository, never())
                .saveAndFlush(any(ChatNotification.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createsOpenKickForTargetUser() {
        stubSaveAndFlush();
        User recipient = user(2L, "kicked");
        ChatRoom room = openRoom(100L, user(1L, "owner"), "open-room");
        LocalDateTime bannedAt = LocalDateTime.of(2026, 8, 11, 14, 10);
        OpenChatMemberBannedApplicationEvent event =
                OpenChatMemberBannedApplicationEvent.of(
                        100L,
                        20L,
                        recipient.getEmail(),
                        "spam",
                        bannedAt
                );

        when(userRepository.findByEmail(recipient.getEmail()))
                .thenReturn(Optional.of(recipient));
        when(chatRoomRepository.findById(100L)).thenReturn(Optional.of(room));

        service.createOpenChatKicked(event);

        ArgumentCaptor<ChatNotification> captor =
                ArgumentCaptor.forClass(ChatNotification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNotificationType())
                .isEqualTo(ChatNotificationType.OPEN_CHAT_KICKED);
        assertThat(captor.getValue().getPayloadJson())
                .contains("open-room")
                .contains("spam");
    }

    @Test
    void createsOpenRoleChangedForTargetMember() {
        stubSaveAndFlush();
        User owner = user(1L, "owner");
        User recipient = user(2L, "target");
        ChatRoom room = openRoom(100L, owner, "open-room");
        ChatRoomMember member = ChatRoomMember.createMember(
                room,
                recipient,
                "ko",
                "ja"
        );
        ReflectionTestUtils.setField(member, "id", 20L);
        OpenChatMemberRoleUpdatedApplicationEvent event =
                OpenChatMemberRoleUpdatedApplicationEvent.of(
                        100L,
                        20L,
                        ChatRoomMemberRole.ADMIN
                );
        when(memberRepository.findById(20L))
                .thenReturn(Optional.of(member));

        service.createOpenChatRoleChanged(event);

        ArgumentCaptor<ChatNotification> captor =
                ArgumentCaptor.forClass(ChatNotification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNotificationType())
                .isEqualTo(ChatNotificationType.OPEN_CHAT_ROLE_CHANGED);
        assertThat(captor.getValue().getPayloadJson())
                .contains("ADMIN")
                .contains("open-room");
    }

    @Test
    void skipsOpenRoleChangedNotificationWhenActorIsRecipient() {
        User recipient = user(2L, "self-role");
        ChatRoom room = openRoom(100L, recipient, "open-room");
        ChatRoomMember member = ChatRoomMember.createOwner(
                room,
                recipient,
                "ko",
                "ja"
        );
        ReflectionTestUtils.setField(member, "id", 20L);
        OpenChatMemberRoleUpdatedApplicationEvent event =
                OpenChatMemberRoleUpdatedApplicationEvent.of(
                        100L,
                        20L,
                        ChatRoomMemberRole.MEMBER,
                        2L
                );
        when(memberRepository.findById(20L))
                .thenReturn(Optional.of(member));

        service.createOpenChatRoleChanged(event);

        verify(notificationRepository, never())
                .saveAndFlush(any(ChatNotification.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createsRoomClosedForActiveParticipantsExceptOwner() {
        stubSaveAndFlush();
        User owner = user(1L, "owner");
        User memberUser = user(2L, "member");
        ChatRoom room = openRoom(100L, owner, "open-room");
        ChatRoomMember ownerMember = ChatRoomMember.createOwner(
                room,
                owner,
                "ko",
                "ja"
        );
        ChatRoomMember member = ChatRoomMember.createMember(
                room,
                memberUser,
                "ko",
                "ja"
        );
        OpenChatRoomClosedApplicationEvent event =
                OpenChatRoomClosedApplicationEvent.of(
                        100L,
                        LocalDateTime.of(2026, 8, 11, 14, 20),
                        1L
                );

        when(chatRoomRepository.findById(100L)).thenReturn(Optional.of(room));
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(memberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNull(100L))
                .thenReturn(List.of(ownerMember, member));

        service.createOpenChatRoomClosed(event);

        ArgumentCaptor<ChatNotification> captor =
                ArgumentCaptor.forClass(ChatNotification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getRecipientUser().getId())
                .isEqualTo(2L);
        assertThat(captor.getValue().getNotificationType())
                .isEqualTo(ChatNotificationType.OPEN_CHAT_ROOM_CLOSED);
        assertThat(captor.getValue().getActorUser().getId())
                .isEqualTo(1L);
    }


    private void stubSaveAndFlush() {
        when(notificationRepository.saveAndFlush(any(ChatNotification.class)))
                .thenAnswer(invocation -> {
                    ChatNotification notification = invocation.getArgument(0);
                    ReflectionTestUtils.setField(notification, "id", 900L);
                    ReflectionTestUtils.setField(
                            notification,
                            "createdAt",
                            LocalDateTime.of(2026, 8, 11, 14, 30)
                    );
                    return notification;
                });
    }

    private User user(Long id, String username) {
        User user = User.createLocalUser(
                username + "@translacat.test",
                "password",
                username,
                Role.USER,
                (username.toUpperCase() + "0000000000").substring(0, 10)
        );
        user.setId(id);
        return user;
    }

    private ChatRoom groupRoom(
            Long id,
            User owner,
            String name
    ) {
        ChatRoom room = ChatRoom.createGroupRoom(name, null, owner);
        ReflectionTestUtils.setField(room, "id", id);
        return room;
    }

    private ChatRoom openRoom(
            Long id,
            User owner,
            String name
    ) {
        ChatRoom room = ChatRoom.createOpenRoom(name, null, owner);
        ReflectionTestUtils.setField(room, "id", id);
        return room;
    }
}
