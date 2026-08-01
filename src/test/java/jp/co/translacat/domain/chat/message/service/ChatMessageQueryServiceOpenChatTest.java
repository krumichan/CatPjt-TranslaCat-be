package jp.co.translacat.domain.chat.message.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.service.ChatRoomMemberQueryService;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageListResponseDto;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMessageSenderResponseDto;
import jp.co.translacat.domain.chat.openchat.profile.service.OpenChatMessageProfileService;
import jp.co.translacat.domain.chat.openchat.service.OpenChatAccessService;
import jp.co.translacat.domain.chat.openchat.support.OpenChatErrorCode;
import jp.co.translacat.domain.chat.read.repository.ChatMessageUnreadMemberCountRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageQueryServiceOpenChatTest {

    @Mock private ChatMessageRepository messageRepository;
    @Mock private ChatMessageTranslationRepository translationRepository;
    @Mock private ChatRoomMemberQueryService memberQueryService;
    @Mock private ChatMessageSenderProfileService ordinaryProfileService;
    @Mock private ChatMessageUnreadMemberCountRepository unreadRepository;
    @Mock private OpenChatMessageProfileService openProfileService;
    @Mock private OpenChatAccessService openChatAccessService;

    private ChatMessageQueryService service;
    private ChatRoom room;
    private ChatRoomMember readerMember;
    private User sender;

    @BeforeEach
    void setUp() {
        service = new ChatMessageQueryService(
                messageRepository,
                translationRepository,
                memberQueryService,
                ordinaryProfileService,
                unreadRepository,
                openProfileService,
                openChatAccessService
        );

        User reader = user(1L, "reader@open.test", "OPENREAD01");
        sender = user(2L, "sender@open.test", "OPENSEND01");
        room = ChatRoom.createOpenRoom("open", "desc", reader);
        ReflectionTestUtils.setField(room, "id", 100L);
        readerMember = ChatRoomMember.createOwner(
                room,
                reader,
                "ko",
                "ja"
        );
        ReflectionTestUtils.setField(
                readerMember,
                "joinedAt",
                LocalDateTime.of(2026, 8, 1, 10, 0)
        );
    }

    @Test
    void bannedOpenChatUserCannotReadMessages() {
        doThrow(new jp.co.translacat.global.exception.BusinessException(
                "banned",
                OpenChatErrorCode.BANNED
        )).when(openChatAccessService)
                .validateOpenRoomMemberAccess(1L, 100L);

        assertThatExceptionOfType(
                jp.co.translacat.global.exception.BusinessException.class
        ).isThrownBy(() -> service.getMessages(1L, 100L, null))
                .satisfies(exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(OpenChatErrorCode.BANNED));

        verify(memberQueryService, never())
                .getActiveMember(1L, 100L);
    }

    @Test
    void openMessagePageUsesRoomProfileWithoutOrdinaryIdentity() {
        ChatMessage message = ChatMessage.createUserTextMessage(
                room,
                sender,
                "hello"
        );
        ReflectionTestUtils.setField(message, "id", 200L);
        ReflectionTestUtils.setField(
                message,
                "createdAt",
                LocalDateTime.of(2026, 8, 1, 11, 0)
        );
        ReflectionTestUtils.setField(
                message,
                "updatedAt",
                LocalDateTime.of(2026, 8, 1, 11, 0)
        );
        OpenChatMessageSenderResponseDto openSender =
                new OpenChatMessageSenderResponseDto(
                        20L,
                        "OC-ABCDE",
                        "room-nickname",
                        null,
                        jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole.MEMBER
                );

        when(memberQueryService.getActiveMember(1L, 100L))
                .thenReturn(readerMember);
        when(messageRepository
                .findTop101ByChatRoomIdAndStatusAndDeletedAtIsNullAndCreatedAtGreaterThanEqualOrderByIdDesc(
                        100L,
                        ChatMessageStatus.SENT,
                        readerMember.getJoinedAt()
                ))
                .thenReturn(List.of(message));
        when(translationRepository
                .findByChatMessageIdInAndDeletedAtIsNull(List.of(200L)))
                .thenReturn(List.of());
        when(unreadRepository
                .countUnreadMembersByMessageIds(List.of(200L)))
                .thenReturn(Map.of(200L, 1L));
        when(openProfileService.resolveMap(100L, Set.of(2L)))
                .thenReturn(Map.of(2L, openSender));

        ChatMessageListResponseDto result = service.getMessages(
                1L,
                100L,
                null
        );

        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().getFirst().sender())
                .isEqualTo(openSender);
        assertThat(result.messages().getFirst().senderUserId()).isNull();
        assertThat(result.messages().getFirst().senderEmail()).isNull();
        verify(ordinaryProfileService, never())
                .resolveLatestProfileImageUrlMap(Set.of(2L));
    }

    private User user(Long id, String email, String publicId) {
        User user = User.createLocalUser(
                email,
                "password",
                email,
                Role.USER,
                publicId
        );
        user.setId(id);
        return user;
    }
}
