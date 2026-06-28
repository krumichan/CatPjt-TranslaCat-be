package jp.co.translacat.domain.chat.room.service;

import jp.co.translacat.domain.user.block.service.UserBlockService;
import jp.co.translacat.domain.user.friend.service.FriendService;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendChatAuthorizationExceptionTest {

    @Mock
    private FriendService friendService;

    @Mock
    private UserBlockService userBlockService;

    @InjectMocks
    private FriendChatValidationService friendChatValidationService;

    @Test
    @DisplayName("미로그인 사용자는 친구 Direct 채팅 검증에 실패한다")
    void failDirectWhenUnauthenticated() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendChatValidationService.validateDirectTarget(null, 2L))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("UNAUTHORIZED")
                );

        verifyNoInteractions(friendService, userBlockService);
    }

    @Test
    @DisplayName("친구가 아닌 사용자는 친구 Direct 채팅 대상이 될 수 없다")
    void failDirectWhenTargetIsNotFriend() {
        // given
        Long loginUserId = 1L;
        Long targetUserId = 2L;

        when(friendService.areFriends(loginUserId, targetUserId)).thenReturn(false);

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendChatValidationService.validateDirectTarget(loginUserId, targetUserId))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_RELATION_REQUIRED")
                );

        verify(userBlockService, never()).isBlockedBetween(anyLong(), anyLong());
    }

    @Test
    @DisplayName("차단 관계 사용자는 친구 Direct 채팅 대상이 될 수 없다")
    void failDirectWhenTargetIsBlocked() {
        // given
        Long loginUserId = 1L;
        Long targetUserId = 2L;

        when(friendService.areFriends(loginUserId, targetUserId)).thenReturn(true);
        when(userBlockService.isBlockedBetween(loginUserId, targetUserId)).thenReturn(true);

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendChatValidationService.validateDirectTarget(loginUserId, targetUserId))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("USER_BLOCKED_BETWEEN")
                );
    }

    @Test
    @DisplayName("자기 자신은 친구 Direct 채팅 대상이 될 수 없다")
    void failDirectWhenTargetIsSelf() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendChatValidationService.validateDirectTarget(1L, 1L))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_CHAT_SELF_NOT_ALLOWED")
                );

        verifyNoInteractions(friendService, userBlockService);
    }

    @Test
    @DisplayName("친구 그룹 채팅 멤버 중 친구가 아닌 사용자가 있으면 실패한다")
    void failGroupWhenMemberIsNotFriend() {
        // given
        Long loginUserId = 1L;
        List<Long> memberUserIds = List.of(2L, 3L);

        when(friendService.areFriends(loginUserId, 2L)).thenReturn(true);
        when(userBlockService.isBlockedBetween(loginUserId, 2L)).thenReturn(false);
        when(friendService.areFriends(loginUserId, 3L)).thenReturn(false);

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendChatValidationService.validateGroupMembers(loginUserId, memberUserIds))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_RELATION_REQUIRED")
                );
    }

    @Test
    @DisplayName("친구 그룹 채팅 멤버 중 차단 관계 사용자가 있으면 실패한다")
    void failGroupWhenMemberIsBlocked() {
        // given
        Long loginUserId = 1L;
        List<Long> memberUserIds = List.of(2L, 3L);

        when(friendService.areFriends(loginUserId, 2L)).thenReturn(true);
        when(userBlockService.isBlockedBetween(loginUserId, 2L)).thenReturn(false);
        when(friendService.areFriends(loginUserId, 3L)).thenReturn(true);
        when(userBlockService.isBlockedBetween(loginUserId, 3L)).thenReturn(true);

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendChatValidationService.validateGroupMembers(loginUserId, memberUserIds))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("USER_BLOCKED_BETWEEN")
                );
    }

    @Test
    @DisplayName("자기 자신은 친구 그룹 채팅 멤버에 포함할 수 없다")
    void failGroupWhenMembersContainSelf() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendChatValidationService.validateGroupMembers(1L, List.of(1L, 2L)))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_GROUP_SELF_MEMBER_NOT_ALLOWED")
                );

        verifyNoInteractions(friendService, userBlockService);
    }
}
