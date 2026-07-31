package jp.co.translacat.domain.chat.message.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.service.ChatRoomMemberQueryService;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.chat.translation.repository.ChatMessageTranslationRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageQueryServiceJoinedAtTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatMessageTranslationRepository translationRepository;

    @Mock
    private ChatRoomMemberQueryService memberQueryService;

    @Mock
    private ChatMessageSenderProfileService senderProfileService;

    @Mock
    private ChatRoomMember currentMember;

    @Mock
    private ChatRoom currentRoom;

    private ChatMessageQueryService service;

    @BeforeEach
    void setUp() {
        service = new ChatMessageQueryService(
                chatMessageRepository,
                translationRepository,
                memberQueryService,
                senderProfileService
        );

        when(currentMember.getChatRoom())
                .thenReturn(currentRoom);
        when(currentRoom.getRoomType())
                .thenReturn(ChatRoomType.GROUP);
    }

    @Test
    void firstPageUsesCurrentMemberJoinedAt() {
        LocalDateTime joinedAt =
                LocalDateTime.of(
                        2026,
                        7,
                        20,
                        12,
                        0
                );

        when(memberQueryService.getActiveMember(
                1L,
                100L
        )).thenReturn(currentMember);

        when(currentMember.getJoinedAt())
                .thenReturn(joinedAt);

        when(chatMessageRepository
                .findTop101ByChatRoomIdAndStatusAndDeletedAtIsNullAndCreatedAtGreaterThanEqualOrderByIdDesc(
                        100L,
                        ChatMessageStatus.SENT,
                        joinedAt
                ))
                .thenReturn(List.of());

        service.getMessages(
                1L,
                100L,
                null
        );

        verify(chatMessageRepository)
                .findTop101ByChatRoomIdAndStatusAndDeletedAtIsNullAndCreatedAtGreaterThanEqualOrderByIdDesc(
                        100L,
                        ChatMessageStatus.SENT,
                        joinedAt
                );

        verify(chatMessageRepository, never())
                .findTop101ByChatRoomIdAndStatusAndDeletedAtIsNullOrderByIdDesc(
                        anyLong(),
                        any()
                );
    }

    @Test
    void cursorPageUsesCurrentMemberJoinedAt() {
        LocalDateTime joinedAt =
                LocalDateTime.of(
                        2026,
                        7,
                        20,
                        12,
                        0
                );

        when(memberQueryService.getActiveMember(
                1L,
                100L
        )).thenReturn(currentMember);

        when(currentMember.getJoinedAt())
                .thenReturn(joinedAt);

        when(chatMessageRepository
                .findTop101ByChatRoomIdAndStatusAndDeletedAtIsNullAndCreatedAtGreaterThanEqualAndIdLessThanOrderByIdDesc(
                        100L,
                        ChatMessageStatus.SENT,
                        joinedAt,
                        500L
                ))
                .thenReturn(List.of());

        service.getMessages(
                1L,
                100L,
                500L
        );

        verify(chatMessageRepository)
                .findTop101ByChatRoomIdAndStatusAndDeletedAtIsNullAndCreatedAtGreaterThanEqualAndIdLessThanOrderByIdDesc(
                        100L,
                        ChatMessageStatus.SENT,
                        joinedAt,
                        500L
                );
    }
}
