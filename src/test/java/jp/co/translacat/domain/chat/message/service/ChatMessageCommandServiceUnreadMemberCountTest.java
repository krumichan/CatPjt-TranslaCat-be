package jp.co.translacat.domain.chat.message.service;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.language.enums.ChatLanguageSettingSource;
import jp.co.translacat.domain.chat.language.service.ChatLanguageSettingResolver;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.member.service.ChatRoomMemberQueryService;
import jp.co.translacat.domain.chat.message.dto.request.ChatMessageCreateRequestDto;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageResponseDto;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.read.repository.ChatMessageUnreadMemberCountRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageCommandServiceUnreadMemberCountTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatMessageTranslationRepository translationRepository;

    @Mock
    private ChatRoomMemberRepository memberRepository;

    @Mock
    private ChatRoomMemberQueryService memberQueryService;

    @Mock
    private ChatLanguageSettingResolver languageSettingResolver;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private ChatWebSocketEventPublisher webSocketEventPublisher;

    @Mock
    private ChatMessageSenderProfileService senderProfileService;

    @Mock
    private ChatMessageUnreadMemberCountRepository unreadCountRepository;

    private ChatMessageCommandService service;
    private ChatRoomMember senderMember;

    @BeforeEach
    void setUp() {
        service = new ChatMessageCommandService(
                chatMessageRepository,
                translationRepository,
                memberRepository,
                memberQueryService,
                languageSettingResolver,
                applicationEventPublisher,
                webSocketEventPublisher,
                senderProfileService,
                unreadCountRepository
        );

        User sender = User.createLocalUser(
                "command-sender@translacat.test",
                "password",
                "command-sender",
                Role.USER,
                "CMDSENDER01"
        );
        ReflectionTestUtils.setField(sender, "id", 1L);
        ChatRoom chatRoom = ChatRoom.createGroupRoom(
                "command-room",
                null,
                sender,
                ChatRoomSourceType.MANUAL
        );
        ReflectionTestUtils.setField(chatRoom, "id", 10L);
        senderMember = ChatRoomMember.createMember(
                chatRoom,
                sender,
                "ko",
                "ja"
        );
    }

    @Test
    void publishesCreatedMessageWithInitialUnreadMemberCount() {
        when(memberQueryService.getActiveMember(1L, 10L))
                .thenReturn(senderMember);
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    ReflectionTestUtils.setField(message, "id", 100L);
                    ReflectionTestUtils.setField(
                            message,
                            "createdAt",
                            LocalDateTime.now()
                    );
                    return message;
                });
        when(memberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNull(10L))
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
        when(senderProfileService.resolveLatestProfileImageUrl(1L))
                .thenReturn(null);
        when(unreadCountRepository.countUnreadMembers(100L))
                .thenReturn(2L);

        ChatMessageResponseDto response = service.createTextMessage(
                1L,
                10L,
                new ChatMessageCreateRequestDto("hello")
        );

        assertThat(response.unreadMemberCount()).isEqualTo(2L);
        verify(webSocketEventPublisher).publishMessageCreated(
                10L,
                response
        );
        verify(unreadCountRepository).countUnreadMembers(100L);
    }
}
