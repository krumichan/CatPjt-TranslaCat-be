package jp.co.translacat.domain.chat.message.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.service.ChatRoomMemberQueryService;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageListResponseDto;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.read.repository.ChatMessageUnreadMemberCountRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.translation.repository.ChatMessageTranslationRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageQueryServiceUnreadMemberCountTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatMessageTranslationRepository translationRepository;

    @Mock
    private ChatRoomMemberQueryService memberQueryService;

    @Mock
    private ChatMessageSenderProfileService senderProfileService;

    @Mock
    private ChatMessageUnreadMemberCountRepository unreadCountRepository;

    private ChatMessageQueryService service;
    private ChatRoom chatRoom;
    private ChatRoomMember currentMember;
    private User sender;

    @BeforeEach
    void setUp() {
        service = new ChatMessageQueryService(
                chatMessageRepository,
                translationRepository,
                memberQueryService,
                senderProfileService,
                unreadCountRepository
        );

        User reader = user(1L, "query-reader@translacat.test", "QRYREAD001");
        sender = user(2L, "query-sender@translacat.test", "QRYSEND001");
        chatRoom = ChatRoom.createGroupRoom(
                "query-room",
                null,
                reader,
                ChatRoomSourceType.MANUAL
        );
        ReflectionTestUtils.setField(chatRoom, "id", 10L);
        currentMember = ChatRoomMember.createMember(
                chatRoom,
                reader,
                "ko",
                "ja"
        );
        ReflectionTestUtils.setField(
                currentMember,
                "joinedAt",
                LocalDateTime.now().minusMinutes(10)
        );
    }

    @Test
    void appliesBatchUnreadMemberCountToInitialMessagePage() {
        ChatMessage message = ChatMessage.createUserTextMessage(
                chatRoom,
                sender,
                "hello"
        );
        ReflectionTestUtils.setField(message, "id", 100L);
        ReflectionTestUtils.setField(
                message,
                "createdAt",
                LocalDateTime.now()
        );

        when(memberQueryService.getActiveMember(1L, 10L))
                .thenReturn(currentMember);
        when(chatMessageRepository
                .findTop101ByChatRoomIdAndStatusAndDeletedAtIsNullAndCreatedAtGreaterThanEqualOrderByIdDesc(
                        10L,
                        ChatMessageStatus.SENT,
                        currentMember.getJoinedAt()
                ))
                .thenReturn(List.of(message));
        when(translationRepository
                .findByChatMessageIdInAndDeletedAtIsNull(List.of(100L)))
                .thenReturn(List.of());
        when(senderProfileService
                .resolveLatestProfileImageUrlMap(java.util.Set.of(2L)))
                .thenReturn(Map.of());
        when(unreadCountRepository
                .countUnreadMembersByMessageIds(List.of(100L)))
                .thenReturn(Map.of(100L, 2L));

        ChatMessageListResponseDto response = service.getMessages(
                1L,
                10L,
                null
        );

        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().getFirst().unreadMemberCount())
                .isEqualTo(2L);
        verify(unreadCountRepository)
                .countUnreadMembersByMessageIds(List.of(100L));
    }


    @Test
    void appliesSameUnreadMemberCountPolicyToCursorPage() {
        ChatMessage message = ChatMessage.createUserTextMessage(
                chatRoom,
                sender,
                "older hello"
        );
        ReflectionTestUtils.setField(message, "id", 90L);
        ReflectionTestUtils.setField(
                message,
                "createdAt",
                LocalDateTime.now().minusMinutes(1)
        );

        when(memberQueryService.getActiveMember(1L, 10L))
                .thenReturn(currentMember);
        when(chatMessageRepository
                .findTop101ByChatRoomIdAndStatusAndDeletedAtIsNullAndCreatedAtGreaterThanEqualAndIdLessThanOrderByIdDesc(
                        10L,
                        ChatMessageStatus.SENT,
                        currentMember.getJoinedAt(),
                        100L
                ))
                .thenReturn(List.of(message));
        when(translationRepository
                .findByChatMessageIdInAndDeletedAtIsNull(List.of(90L)))
                .thenReturn(List.of());
        when(senderProfileService
                .resolveLatestProfileImageUrlMap(java.util.Set.of(2L)))
                .thenReturn(Map.of());
        when(unreadCountRepository
                .countUnreadMembersByMessageIds(List.of(90L)))
                .thenReturn(Map.of(90L, 1L));

        ChatMessageListResponseDto response = service.getMessages(
                1L,
                10L,
                100L
        );

        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().getFirst().unreadMemberCount())
                .isEqualTo(1L);
        verify(unreadCountRepository)
                .countUnreadMembersByMessageIds(List.of(90L));
    }

    private User user(Long id, String email, String publicId) {
        User user = User.createLocalUser(
                email,
                "password",
                email,
                Role.USER,
                publicId
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
