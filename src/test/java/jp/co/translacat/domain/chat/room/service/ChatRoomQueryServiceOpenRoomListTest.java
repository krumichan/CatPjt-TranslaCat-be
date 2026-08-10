package jp.co.translacat.domain.chat.room.service;

import jp.co.translacat.domain.chat.language.service.ChatLanguageSettingResolver;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.openchat.repository.OpenChatRoomRepository;
import jp.co.translacat.domain.chat.presence.service.ChatPresenceQueryService;
import jp.co.translacat.domain.chat.read.repository.ChatUnreadCountRepository;
import jp.co.translacat.domain.chat.room.dto.response.ChatRoomListResponseDto;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.domain.user.profile.repository.UserProfileRepository;
import jp.co.translacat.domain.user.profile.storage.service.UserProfileImageUrlResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomQueryServiceOpenRoomListTest {

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private OpenChatRoomRepository openChatRoomRepository;
    @Mock private ChatLanguageSettingResolver chatLanguageSettingResolver;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private UserProfileImageUrlResolver imageUrlResolver;
    @Mock private ChatUnreadCountRepository chatUnreadCountRepository;
    @Mock private ChatPresenceQueryService chatPresenceQueryService;

    @Mock private ChatRoomMember openMember;
    @Mock private ChatRoomMember groupMember;
    @Mock private ChatRoom closedOpenRoom;
    @Mock private ChatRoom groupRoom;

    private ChatRoomQueryService service;

    @BeforeEach
    void setUp() {
        service = new ChatRoomQueryService(
                chatRoomRepository,
                chatRoomMemberRepository,
                openChatRoomRepository,
                chatLanguageSettingResolver,
                userProfileRepository,
                imageUrlResolver,
                chatUnreadCountRepository,
                chatPresenceQueryService
        );
    }

    @Test
    void getMyChatRooms_ExcludesClosedOpenRoom() {
        when(chatRoomMemberRepository
                .findByUserIdAndActiveTrueAndDeletedAtIsNull(1L))
                .thenReturn(List.of(openMember, groupMember));
        when(openMember.getChatRoom()).thenReturn(closedOpenRoom);
        when(groupMember.getChatRoom()).thenReturn(groupRoom);

        when(closedOpenRoom.isActive()).thenReturn(true);
        when(closedOpenRoom.isDeleted()).thenReturn(false);
        when(closedOpenRoom.getRoomType()).thenReturn(ChatRoomType.OPEN);
        when(closedOpenRoom.getId()).thenReturn(120001L);

        when(groupRoom.isActive()).thenReturn(true);
        when(groupRoom.isDeleted()).thenReturn(false);
        when(groupRoom.getRoomType()).thenReturn(ChatRoomType.GROUP);
        when(groupRoom.getId()).thenReturn(220001L);
        when(groupRoom.getUpdatedAt())
                .thenReturn(LocalDateTime.of(2026, 8, 10, 23, 0));
        when(groupRoom.getSourceType())
                .thenReturn(ChatRoomSourceType.MANUAL);
        when(groupRoom.getName()).thenReturn("group");
        when(groupRoom.getDescription()).thenReturn("group room");
        when(groupRoom.getCreatedAt())
                .thenReturn(LocalDateTime.of(2026, 8, 10, 22, 0));
        when(groupRoom.getOwner()).thenReturn(null);

        when(openChatRoomRepository.findActiveRoomIds(
                List.of(120001L)
        )).thenReturn(Set.of());
        when(chatRoomMemberRepository
                .findByChatRoomIdInAndActiveTrueAndDeletedAtIsNull(
                        List.of(220001L)
                ))
                .thenReturn(List.of());
        when(chatUnreadCountRepository.countUnreadByRoomIds(
                1L,
                List.of(220001L)
        )).thenReturn(Map.of());

        ChatRoomListResponseDto result = service.getMyChatRooms(1L);

        assertThat(result.chatRooms())
                .extracting(item -> item.id())
                .containsExactly(220001L);
    }

    @Test
    void getMyChatRooms_KeepsActiveOpenRoom() {
        when(chatRoomMemberRepository
                .findByUserIdAndActiveTrueAndDeletedAtIsNull(1L))
                .thenReturn(List.of(openMember));
        when(openMember.getChatRoom()).thenReturn(closedOpenRoom);

        when(closedOpenRoom.isActive()).thenReturn(true);
        when(closedOpenRoom.isDeleted()).thenReturn(false);
        when(closedOpenRoom.getRoomType()).thenReturn(ChatRoomType.OPEN);
        when(closedOpenRoom.getId()).thenReturn(120001L);
        when(closedOpenRoom.getUpdatedAt())
                .thenReturn(LocalDateTime.of(2026, 8, 10, 23, 0));
        when(closedOpenRoom.getSourceType())
                .thenReturn(ChatRoomSourceType.OPEN);
        when(closedOpenRoom.getName()).thenReturn("open");
        when(closedOpenRoom.getDescription()).thenReturn("open room");
        when(closedOpenRoom.getCreatedAt())
                .thenReturn(LocalDateTime.of(2026, 8, 10, 22, 0));

        when(openChatRoomRepository.findActiveRoomIds(
                List.of(120001L)
        )).thenReturn(Set.of(120001L));
        when(chatRoomMemberRepository
                .findByChatRoomIdInAndActiveTrueAndDeletedAtIsNull(
                        List.of(120001L)
                ))
                .thenReturn(List.of());
        when(chatUnreadCountRepository.countUnreadByRoomIds(
                1L,
                List.of(120001L)
        )).thenReturn(Map.of());

        ChatRoomListResponseDto result = service.getMyChatRooms(1L);

        assertThat(result.chatRooms())
                .extracting(item -> item.id())
                .containsExactly(120001L);
    }
}
