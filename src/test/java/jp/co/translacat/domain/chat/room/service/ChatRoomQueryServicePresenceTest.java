package jp.co.translacat.domain.chat.room.service;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.language.service.ChatLanguageSettingResolver;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.presence.service.ChatPresenceQueryService;
import jp.co.translacat.domain.chat.openchat.repository.OpenChatRoomRepository;
import jp.co.translacat.domain.chat.read.repository.ChatUnreadCountRepository;
import jp.co.translacat.domain.chat.room.dto.response.ChatRoomResponseDto;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.profile.repository.UserProfileRepository;
import jp.co.translacat.domain.user.profile.storage.service.UserProfileImageUrlResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomQueryServicePresenceTest {

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private OpenChatRoomRepository openChatRoomRepository;
    @Mock private ChatLanguageSettingResolver chatLanguageSettingResolver;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private UserProfileImageUrlResolver imageUrlResolver;
    @Mock private ChatUnreadCountRepository chatUnreadCountRepository;
    @Mock private ChatPresenceQueryService chatPresenceQueryService;

    @Mock private ChatRoom room;
    @Mock private ChatRoomMember loginMember;
    @Mock private ChatRoomMember partnerMember;
    @Mock private User loginUser;
    @Mock private User partnerUser;

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
    void getChatRoom_IncludesDirectPartnerPresenceSnapshot() {
        when(chatRoomMemberRepository
                .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                        100L,
                        1L
                ))
                .thenReturn(Optional.of(loginMember));
        when(loginMember.getChatRoom()).thenReturn(room);
        when(room.isActive()).thenReturn(true);
        when(room.isDeleted()).thenReturn(false);
        when(room.getId()).thenReturn(100L);
        when(room.getRoomType()).thenReturn(ChatRoomType.DIRECT);
        when(room.getSourceType()).thenReturn(ChatRoomSourceType.FRIEND);
        when(chatLanguageSettingResolver.resolve(loginMember))
                .thenReturn(new ChatLanguageSettingResult(
                        "ko",
                        "ja",
                        false
                ));
        when(chatRoomMemberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNull(100L))
                .thenReturn(List.of(loginMember, partnerMember));
        when(loginMember.getUser()).thenReturn(loginUser);
        when(partnerMember.getUser()).thenReturn(partnerUser);
        when(loginUser.getId()).thenReturn(1L);
        when(partnerUser.getId()).thenReturn(2L);
        when(partnerUser.getPublicId()).thenReturn("TC-DIRECT-PARTNER");
        when(partnerUser.getUsername()).thenReturn("partner");
        when(userProfileRepository.findByUserIdInAndDeletedFalse(
                anyCollection()
        )).thenReturn(List.of());
        when(chatPresenceQueryService.resolveOnline(2L)).thenReturn(true);

        ChatRoomResponseDto result = service.getChatRoom(1L, 100L);

        assertThat(result.directPartner()).isNotNull();
        assertThat(result.directPartner().publicId())
                .isEqualTo("TC-DIRECT-PARTNER");
        assertThat(result.directPartner().online()).isTrue();
    }
}
