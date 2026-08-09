package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.entity.ChatAiAgent;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiMemberRepository;
import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.language.enums.ChatLanguageSettingSource;
import jp.co.translacat.domain.chat.language.service.ChatLanguageSettingResolver;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageResponseDto;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.read.repository.ChatMessageUnreadMemberCountRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.translation.entity.ChatMessageTranslation;
import jp.co.translacat.domain.chat.translation.event.ChatMessageTranslationRequestedEvent;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAiMessageCommandServiceTest {

    @Mock private ChatMessageRepository messageRepository;
    @Mock private ChatRoomAiMemberRepository aiMemberRepository;
    @Mock private ChatRoomMemberRepository roomMemberRepository;
    @Mock private ChatMessageTranslationRepository translationRepository;
    @Mock private ChatLanguageSettingResolver languageSettingResolver;
    @Mock private ChatMessageUnreadMemberCountRepository unreadCountRepository;
    @Mock private ChatAiProfileImageUrlResolver profileImageUrlResolver;
    @Mock private ChatWebSocketEventPublisher webSocketEventPublisher;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    private ChatAiMessageCommandService service;
    private ChatRoomAiMember aiMember;
    private ChatRoomMember humanMember;

    @BeforeEach
    void setUp() {
        service = new ChatAiMessageCommandService(
                messageRepository,
                aiMemberRepository,
                roomMemberRepository,
                translationRepository,
                languageSettingResolver,
                unreadCountRepository,
                profileImageUrlResolver,
                webSocketEventPublisher,
                applicationEventPublisher
        );

        User owner = User.createLocalUser(
                "owner@ai-message.test",
                "password",
                "owner",
                Role.USER,
                "AIMESSAGEOWN"
        );
        ReflectionTestUtils.setField(owner, "id", 1L);
        ChatRoom room = ChatRoom.createGroupRoom("group", null, owner);
        ReflectionTestUtils.setField(room, "id", 10L);

        ChatAiAgent agent = ChatAiAgent.create(
                "Mika",
                null,
                "ja",
                "persona"
        );
        ReflectionTestUtils.setField(agent, "id", 20L);
        aiMember = ChatRoomAiMember.create(room, agent);
        ReflectionTestUtils.setField(aiMember, "id", 30L);

        humanMember = ChatRoomMember.createOwner(
                room,
                owner,
                "ko",
                "en"
        );
    }

    @Test
    void duplicateRequestIdIsSkippedBeforeSaving() {
        when(messageRepository.existsByAiRequestId("req-1"))
                .thenReturn(true);

        ChatMessageResponseDto response = service.createAiTextMessage(
                10L,
                30L,
                "req-1",
                "こんにちは"
        );

        assertThat(response).isNull();
        verify(aiMemberRepository, never())
                .findByIdAndChatRoomIdAndActiveTrueAndDeletedAtIsNull(
                        30L,
                        10L
                );
        verify(messageRepository, never()).saveAndFlush(any());
    }

    @Test
    void aiMessageUsesExistingTranslationAndWebSocketPipeline() {
        when(messageRepository.existsByAiRequestId("req-2"))
                .thenReturn(false);
        when(aiMemberRepository
                .findByIdAndChatRoomIdAndActiveTrueAndDeletedAtIsNull(
                        30L,
                        10L
                )).thenReturn(Optional.of(aiMember));
        when(messageRepository.saveAndFlush(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    ReflectionTestUtils.setField(message, "id", 100L);
                    ReflectionTestUtils.setField(
                            message,
                            "createdAt",
                            LocalDateTime.now()
                    );
                    ReflectionTestUtils.setField(
                            message,
                            "updatedAt",
                            LocalDateTime.now()
                    );
                    return message;
                });
        when(roomMemberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNull(10L))
                .thenReturn(List.of(humanMember));
        when(languageSettingResolver.resolve(humanMember))
                .thenReturn(new ChatLanguageSettingResult(
                        "ko",
                        "en",
                        true,
                        true,
                        false,
                        ChatLanguageSettingSource.SYSTEM
                ));
        when(translationRepository.saveAll(anyList()))
                .thenAnswer(invocation -> {
                    List<ChatMessageTranslation> values = invocation.getArgument(0);
                    long id = 500L;
                    for (Object value : values) {
                        ReflectionTestUtils.setField(value, "id", id++);
                    }
                    return values;
                });
        when(unreadCountRepository.countUnreadMembers(100L))
                .thenReturn(1L);
        when(profileImageUrlResolver.resolveProfileImageUrl(
                aiMember.getAiAgent()
        )).thenReturn("https://cdn.example/mika.png");

        ChatMessageResponseDto response = service.createAiTextMessage(
                10L,
                30L,
                "req-2",
                "こんにちは"
        );

        assertThat(response).isNotNull();
        assertThat(response.senderAiMemberId()).isEqualTo(30L);
        assertThat(response.senderName()).isEqualTo("Mika");
        assertThat(response.senderUserId()).isNull();
        assertThat(response.translations()).hasSize(1);
        verify(webSocketEventPublisher).publishMessageCreated(10L, response);
        verify(applicationEventPublisher).publishEvent(
                any(ChatMessageTranslationRequestedEvent.class)
        );
    }
}
