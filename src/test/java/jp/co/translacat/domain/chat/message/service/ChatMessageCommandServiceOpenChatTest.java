package jp.co.translacat.domain.chat.message.service;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.language.enums.ChatLanguageSettingSource;
import jp.co.translacat.domain.chat.language.service.ChatLanguageSettingResolver;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.member.service.ChatRoomMemberQueryService;
import jp.co.translacat.domain.chat.message.dto.request.ChatMessageCreateRequestDto;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageResponseDto;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMessageSenderResponseDto;
import jp.co.translacat.domain.chat.openchat.profile.service.OpenChatMessageProfileService;
import jp.co.translacat.domain.chat.openchat.service.OpenChatAccessService;
import jp.co.translacat.domain.chat.openchat.support.OpenChatErrorCode;
import jp.co.translacat.domain.chat.read.repository.ChatMessageUnreadMemberCountRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.translation.repository.ChatMessageTranslationRepository;
import jp.co.translacat.domain.chat.websocket.service.ChatWebSocketEventPublisher;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageCommandServiceOpenChatTest {

    @Mock private ChatMessageRepository messageRepository;
    @Mock private ChatMessageTranslationRepository translationRepository;
    @Mock private ChatRoomMemberRepository memberRepository;
    @Mock private ChatRoomMemberQueryService memberQueryService;
    @Mock private ChatLanguageSettingResolver languageSettingResolver;
    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @Mock private ChatWebSocketEventPublisher webSocketEventPublisher;
    @Mock private ChatMessageSenderProfileService ordinaryProfileService;
    @Mock private ChatMessageUnreadMemberCountRepository unreadRepository;
    @Mock private OpenChatAccessService openChatAccessService;
    @Mock private OpenChatMessageProfileService openProfileService;

    private ChatMessageCommandService service;
    private ChatRoomMember senderMember;

    @BeforeEach
    void setUp() {
        service = new ChatMessageCommandService(
                messageRepository,
                translationRepository,
                memberRepository,
                memberQueryService,
                languageSettingResolver,
                applicationEventPublisher,
                webSocketEventPublisher,
                ordinaryProfileService,
                unreadRepository,
                openChatAccessService,
                openProfileService
        );

        User sender = User.createLocalUser(
                "sender@open.test",
                "password",
                "ordinary-username",
                Role.USER,
                "OPENSEND01"
        );
        sender.setId(2L);
        ChatRoom room = ChatRoom.createOpenRoom("open", "desc", sender);
        ReflectionTestUtils.setField(room, "id", 100L);
        senderMember = ChatRoomMember.createOwner(
                room,
                sender,
                "ko",
                "ja"
        );
    }

    @Test
    void bannedOpenChatUserCannotSendMessage() {
        doThrow(new jp.co.translacat.global.exception.BusinessException(
                "banned",
                OpenChatErrorCode.BANNED
        )).when(openChatAccessService)
                .validateOpenRoomMemberAccess(2L, 100L);

        assertThatExceptionOfType(
                jp.co.translacat.global.exception.BusinessException.class
        ).isThrownBy(() -> service.createTextMessage(
                2L,
                100L,
                new ChatMessageCreateRequestDto("hello")
        )).satisfies(exception -> assertThat(
                exception.getErrorCode()
        ).isEqualTo(OpenChatErrorCode.BANNED));

        verify(memberQueryService, never())
                .getActiveMember(2L, 100L);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void createsOpenMessageWithRoomScopedSender() {
        OpenChatMessageSenderResponseDto openSender =
                new OpenChatMessageSenderResponseDto(
                        20L,
                        "OC-ABCDE",
                        "room-nickname",
                        null,
                        ChatRoomMemberRole.OWNER
                );

        when(memberQueryService.getActiveMember(2L, 100L))
                .thenReturn(senderMember);
        when(messageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    ReflectionTestUtils.setField(message, "id", 200L);
                    ReflectionTestUtils.setField(
                            message,
                            "createdAt",
                            LocalDateTime.of(2026, 8, 1, 12, 0)
                    );
                    ReflectionTestUtils.setField(
                            message,
                            "updatedAt",
                            LocalDateTime.of(2026, 8, 1, 12, 0)
                    );
                    return message;
                });
        when(memberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNull(100L))
                .thenReturn(List.of());
        when(languageSettingResolver.resolve(senderMember))
                .thenReturn(new ChatLanguageSettingResult(
                        "ko",
                        "ja",
                        true,
                        true,
                        false,
                        ChatLanguageSettingSource.SYSTEM
                ));
        when(translationRepository.saveAll(anyList()))
                .thenReturn(List.of());
        when(unreadRepository.countUnreadMembers(200L))
                .thenReturn(1L);
        when(openProfileService.resolve(100L, 2L))
                .thenReturn(openSender);

        ChatMessageResponseDto result = service.createTextMessage(
                2L,
                100L,
                new ChatMessageCreateRequestDto("hello")
        );

        verify(openChatAccessService)
                .validateMessageSendAllowed(senderMember);
        assertThat(result.sender()).isEqualTo(openSender);
        assertThat(result.senderUserId()).isNull();
        assertThat(result.senderName()).isNull();
        assertThat(result.senderEmail()).isNull();
        verify(ordinaryProfileService, never())
                .resolveLatestProfileImageUrl(2L);
        verify(webSocketEventPublisher)
                .publishMessageCreated(100L, result);
    }
}
