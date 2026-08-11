package jp.co.translacat.domain.chat.message.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.service.ChatRoomMemberQueryService;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageAnchorListResponseDto;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.message.repository.projection.ChatMessageAnchorWindowQueryResult;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageQueryServiceAnchorTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatMessageTranslationRepository translationRepository;

    @Mock
    private ChatRoomMemberQueryService memberQueryService;

    @Mock
    private ChatMessageSenderProfileService senderProfileService;

    private ChatMessageQueryService service;
    private ChatRoom room;
    private ChatRoomMember member;
    private User reader;
    private User sender;
    private LocalDateTime joinedAt;

    @BeforeEach
    void setUp() {
        service = new ChatMessageQueryService(
                chatMessageRepository,
                translationRepository,
                memberQueryService,
                senderProfileService
        );

        reader = createUser(
                1L,
                "anchor-reader@translacat.test",
                "anchor-reader",
                "ANCHREAD01"
        );
        sender = createUser(
                2L,
                "anchor-sender@translacat.test",
                "anchor-sender",
                "ANCHSEND01"
        );
        room = ChatRoom.createGroupRoom(
                "anchor-room",
                null,
                reader,
                ChatRoomSourceType.MANUAL
        );
        ReflectionTestUtils.setField(room, "id", 10L);
        member = ChatRoomMember.createMember(
                room,
                reader,
                "ko",
                "ja"
        );
        joinedAt = LocalDateTime.now().minusMinutes(10);
        ReflectionTestUtils.setField(member, "joinedAt", joinedAt);
    }

    @Test
    void anchorLoadFetchesTranslationsOnlyForReturnedWindow() {
        ChatMessage message1000 = createMessage(1000L, "m1000");
        ChatMessage message1001 = createMessage(1001L, "m1001");
        ChatMessage message1002 = createMessage(1002L, "m1002");

        when(memberQueryService.getActiveMember(1L, 10L))
                .thenReturn(member);
        when(chatMessageRepository
                .findByIdAndChatRoomIdAndDeletedAtIsNull(1001L, 10L))
                .thenReturn(Optional.of(message1001));
        when(chatMessageRepository.findAnchorWindowIds(
                10L,
                1001L,
                joinedAt,
                1,
                1
        )).thenReturn(new ChatMessageAnchorWindowQueryResult(
                List.of(1000L, 1001L, 1002L),
                1000L,
                true,
                1002L,
                true
        ));
        when(chatMessageRepository.findByIdInAndDeletedAtIsNull(
                List.of(1000L, 1001L, 1002L)
        )).thenReturn(List.of(
                message1000,
                message1001,
                message1002
        ));
        when(translationRepository
                .findByChatMessageIdInAndDeletedAtIsNull(
                        List.of(1000L, 1001L, 1002L)
                ))
                .thenReturn(List.of());
        when(senderProfileService.resolveLatestProfileImageUrlMap(
                java.util.Set.of(2L)
        )).thenReturn(Map.of());

        ChatMessageAnchorListResponseDto response =
                service.getMessagesAroundAnchor(
                        1L,
                        10L,
                        1001L,
                        1,
                        1
                );

        assertThat(response.messages())
                .extracting(message -> message.id())
                .containsExactly(1000L, 1001L, 1002L);
        assertThat(response.anchorMessageId()).isEqualTo(1001L);
        assertThat(response.hasPrevious()).isTrue();
        assertThat(response.hasNext()).isTrue();

        verify(translationRepository)
                .findByChatMessageIdInAndDeletedAtIsNull(
                        List.of(1000L, 1001L, 1002L)
                );
    }

    @Test
    void forwardPageLoadsOnlyNextPageAndReturnsContinuationCursor() {
        ChatMessage cursorMessage = createMessage(1000L, "m1000");
        ChatMessage message1001 = createMessage(1001L, "m1001");
        ChatMessage message1002 = createMessage(1002L, "m1002");

        when(memberQueryService.getActiveMember(1L, 10L))
                .thenReturn(member);
        when(chatMessageRepository
                .findByIdAndChatRoomIdAndDeletedAtIsNull(1000L, 10L))
                .thenReturn(Optional.of(cursorMessage));
        when(chatMessageRepository.findNextMessageIds(
                10L,
                1000L,
                joinedAt,
                3
        )).thenReturn(List.of(1001L, 1002L, 1003L));
        when(chatMessageRepository.findByIdInAndDeletedAtIsNull(
                List.of(1001L, 1002L)
        )).thenReturn(List.of(message1001, message1002));
        when(translationRepository
                .findByChatMessageIdInAndDeletedAtIsNull(
                        List.of(1001L, 1002L)
                ))
                .thenReturn(List.of());
        when(senderProfileService.resolveLatestProfileImageUrlMap(
                java.util.Set.of(2L)
        )).thenReturn(Map.of());

        var response = service.getMessagesAfter(
                1L,
                10L,
                1000L,
                2
        );

        assertThat(response.messages())
                .extracting(message -> message.id())
                .containsExactly(1001L, 1002L);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursorId()).isEqualTo(1002L);
    }

    private ChatMessage createMessage(Long id, String content) {
        ChatMessage message = ChatMessage.createUserTextMessage(
                room,
                sender,
                content
        );
        ReflectionTestUtils.setField(message, "id", id);
        ReflectionTestUtils.setField(
                message,
                "createdAt",
                joinedAt.plusMinutes(1)
        );
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
