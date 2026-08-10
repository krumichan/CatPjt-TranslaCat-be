package jp.co.translacat.domain.chat.member.service;

import jp.co.translacat.domain.chat.ai.dto.response.ChatAiDisplayMembersResponseDto;
import jp.co.translacat.domain.chat.ai.service.ChatAiDisplayMemberService;
import jp.co.translacat.domain.chat.language.service.ChatLanguageSettingResolver;
import jp.co.translacat.domain.chat.member.dto.response.ChatRoomMemberListResponseDto;
import jp.co.translacat.domain.chat.member.dto.response.ChatRoomMemberProfileResponseDto;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.chat.presence.service.ChatPresenceQueryService;
import jp.co.translacat.domain.chat.presence.service.ChatPresenceVisibilityPolicy;
import jp.co.translacat.domain.user.block.service.UserBlockService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.friend.request.enums.FriendRequestStatus;
import jp.co.translacat.domain.user.friend.request.repository.FriendRequestRepository;
import jp.co.translacat.domain.user.friend.service.FriendService;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;
import jp.co.translacat.domain.user.profile.service.UserProfileQueryService;
import jp.co.translacat.domain.user.search.enums.UserSearchFriendStatus;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomMemberQueryServiceTest {
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Mock
    private ChatAiDisplayMemberService chatAiDisplayMemberService;

    @Mock
    private ChatLanguageSettingResolver chatLanguageSettingResolver;

    @Mock
    private UserProfileQueryService userProfileQueryService;

    @Mock
    private FriendService friendService;

    @Mock
    private FriendRequestRepository friendRequestRepository;

    @Mock
    private UserBlockService userBlockService;

    @Mock
    private ChatPresenceQueryService chatPresenceQueryService;

    @Mock
    private ChatPresenceVisibilityPolicy chatPresenceVisibilityPolicy;

    @Mock
    private ChatRoom chatRoom;

    @Mock
    private ChatRoomMember loginMember;

    @Mock
    private ChatRoomMember targetMember;
    @Mock
    private User targetUser;

    private ChatRoomMemberQueryService service;

    @BeforeEach
    void setUp() {
        service = new ChatRoomMemberQueryService(
                chatRoomMemberRepository,
                chatAiDisplayMemberService,
                chatLanguageSettingResolver,
                userProfileQueryService,
                friendService,
                friendRequestRepository,
                userBlockService,
                chatPresenceQueryService,
                chatPresenceVisibilityPolicy
        );
    }

    @Test
    void getMembersIncludesPresenceSnapshotForHumanMembers() {
        Long loginUserId = 1L;
        Long chatRoomId = 501L;
        Long targetUserId = 2L;
        LocalDateTime joinedAt = LocalDateTime.of(2026, 8, 10, 20, 0);

        stubGeneralProfileRoom();
        when(chatRoom.getId()).thenReturn(chatRoomId);
        when(chatRoomMemberRepository
                .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                        chatRoomId,
                        loginUserId
                ))
                .thenReturn(Optional.of(loginMember));
        when(chatRoomMemberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNull(chatRoomId))
                .thenReturn(List.of(targetMember));
        when(targetMember.getChatRoom()).thenReturn(chatRoom);
        when(targetMember.getUser()).thenReturn(targetUser);
        when(targetMember.getJoinedAt()).thenReturn(joinedAt);
        when(targetUser.getId()).thenReturn(targetUserId);
        when(userProfileQueryService.getSummaryByUser(targetUser))
                .thenReturn(new UserSummaryProfileResponseDto(
                        targetUserId,
                        "TC-GROUP-MEMBER",
                        "group-member",
                        null
                ));
        when(chatPresenceVisibilityPolicy.isVisible(chatRoomId))
                .thenReturn(true);
        when(chatPresenceQueryService.resolveOnlineByUserIds(
                List.of(targetUserId)
        )).thenReturn(Map.of(targetUserId, true));
        when(chatAiDisplayMemberService.getDisplayMembers(chatRoomId))
                .thenReturn(ChatAiDisplayMembersResponseDto.empty());

        ChatRoomMemberListResponseDto result = service.getMembers(
                loginUserId,
                chatRoomId
        );

        assertThat(result.members()).hasSize(1);
        assertThat(result.members().get(0).online()).isTrue();
        assertThat(result.members().get(0).publicId())
                .isEqualTo("TC-GROUP-MEMBER");
    }

    @Test
    void getMemberProfileReturnsLatestProfileAndFriendStatus() {
        Long loginUserId = 1L;
        Long chatRoomId = 501L;
        Long targetUserId = 2L;

        stubGeneralProfileRoom();

        when(chatRoomMemberRepository
                .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                        chatRoomId,
                        loginUserId
                ))
                .thenReturn(Optional.of(loginMember));
        when(chatRoomMemberRepository
                .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                        chatRoomId,
                        targetUserId
                ))
                .thenReturn(Optional.of(targetMember));

        when(targetMember.getUser()).thenReturn(targetUser);
        when(targetUser.getId()).thenReturn(targetUserId);
        UserSummaryProfileResponseDto profile =
                new UserSummaryProfileResponseDto(
                        targetUserId,
                        "TC-ABCD-EFGH",
                        "그룹 멤버",
                        "https://cdn.example.com/profile.png",
                        "https://cdn.example.com/background.png",
                        "상태 메시지"
                );
        when(userProfileQueryService.getSummaryByUser(targetUser))
                .thenReturn(profile);
        when(userBlockService.isBlockedBetween(
                loginUserId,
                targetUserId
        )).thenReturn(false);
        when(friendService.areFriends(
                loginUserId,
                targetUserId
        )).thenReturn(false);
        when(friendRequestRepository.findBetweenUsersByStatus(
                loginUserId,
                targetUserId,
                FriendRequestStatus.PENDING
        )).thenReturn(Optional.empty());
        when(chatPresenceVisibilityPolicy.isVisible(chatRoomId))
                .thenReturn(true);
        when(chatPresenceQueryService.resolveOnline(targetUserId))
                .thenReturn(true);
        ChatRoomMemberProfileResponseDto result =
                service.getMemberProfile(
                        loginUserId,
                        chatRoomId,
                        targetUserId
                );
        assertThat(result.userId()).isEqualTo(targetUserId);
        assertThat(result.publicId()).isEqualTo("TC-ABCD-EFGH");
        assertThat(result.displayName()).isEqualTo("그룹 멤버");
        assertThat(result.profileImageUrl())
                .isEqualTo("https://cdn.example.com/profile.png");
        assertThat(result.profileBackgroundImageUrl())
                .isEqualTo("https://cdn.example.com/background.png");
        assertThat(result.bio()).isEqualTo("상태 메시지");
        assertThat(result.friendStatus())
                .isEqualTo(UserSearchFriendStatus.NONE);
        assertThat(result.online()).isTrue();
    }
    @Test
    void getMemberProfileRejectsNonMemberRequester() {
        when(chatRoomMemberRepository
                .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                        501L,
                        1L
                ))
                .thenReturn(Optional.empty());
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getMemberProfile(
                        1L,
                        501L,
                        2L
                ));

        verifyNoInteractions(
                userProfileQueryService,
                friendService,
                friendRequestRepository,
                userBlockService,
                chatPresenceQueryService,
                chatPresenceVisibilityPolicy
        );
    }
    @Test
    void getMemberProfileRejectsTargetOutsideRoom() {
        stubGeneralProfileRoom();

        when(chatRoomMemberRepository
                .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                        501L,
                        1L
                ))
                .thenReturn(Optional.of(loginMember));
        when(chatRoomMemberRepository
                .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                        501L,
                        2L
                ))
                .thenReturn(Optional.empty());

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getMemberProfile(
                        1L,
                        501L,
                        2L
                ));
        verifyNoInteractions(
                userProfileQueryService,
                friendService,
                friendRequestRepository,
                userBlockService,
                chatPresenceQueryService,
                chatPresenceVisibilityPolicy
        );
    }

    private void stubGeneralProfileRoom() {
        when(loginMember.getChatRoom()).thenReturn(chatRoom);
        when(chatRoom.getRoomType()).thenReturn(ChatRoomType.GROUP);
    }
}
