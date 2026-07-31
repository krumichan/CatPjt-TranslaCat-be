package jp.co.translacat.domain.chat.member.service;

import jp.co.translacat.domain.chat.language.service.ChatLanguageSettingResolver;
import jp.co.translacat.domain.chat.member.dto.response.ChatRoomMemberProfileResponseDto;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomMemberQueryServiceTest {
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

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
                chatLanguageSettingResolver,
                userProfileQueryService,
                friendService,
                friendRequestRepository,
                userBlockService
        );
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
                userBlockService
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
                userBlockService
        );
    }

    private void stubGeneralProfileRoom() {
        when(loginMember.getChatRoom()).thenReturn(chatRoom);
        when(chatRoom.getRoomType()).thenReturn(ChatRoomType.GROUP);
    }
}
