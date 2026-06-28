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
class FriendChatValidationServiceTest {

    @Mock
    private FriendService friendService;

    @Mock
    private UserBlockService userBlockService;

    @InjectMocks
    private FriendChatValidationService friendChatValidationService;

    @Test
    @DisplayName("친구 Direct 대상 검증 성공")
    void validateDirectTarget() {
        // given
        Long loginUserId = 1L;
        Long friendUserId = 2L;

        when(friendService.areFriends(loginUserId, friendUserId)).thenReturn(true);
        when(userBlockService.isBlockedBetween(loginUserId, friendUserId)).thenReturn(false);

        // when
        friendChatValidationService.validateDirectTarget(loginUserId, friendUserId);

        // then
        verify(friendService).areFriends(loginUserId, friendUserId);
        verify(userBlockService).isBlockedBetween(loginUserId, friendUserId);
    }

    @Test
    @DisplayName("친구가 아닌 사용자는 Direct 대상이 될 수 없다")
    void failWhenDirectTargetIsNotFriend() {
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
    @DisplayName("차단 관계 사용자는 Direct 대상이 될 수 없다")
    void failWhenDirectTargetIsBlocked() {
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
    @DisplayName("그룹 멤버 일괄 검증 성공")
    void validateGroupMembers() {
        // given
        Long loginUserId = 1L;
        List<Long> memberUserIds = List.of(2L, 3L, 3L);

        when(friendService.areFriends(loginUserId, 2L)).thenReturn(true);
        when(friendService.areFriends(loginUserId, 3L)).thenReturn(true);
        when(userBlockService.isBlockedBetween(loginUserId, 2L)).thenReturn(false);
        when(userBlockService.isBlockedBetween(loginUserId, 3L)).thenReturn(false);

        // when
        friendChatValidationService.validateGroupMembers(loginUserId, memberUserIds);

        // then
        verify(friendService).areFriends(loginUserId, 2L);
        verify(friendService).areFriends(loginUserId, 3L);
        verify(friendService, times(2)).areFriends(eq(loginUserId), anyLong());
    }

    @Test
    @DisplayName("그룹 멤버가 비어 있으면 실패한다")
    void failWhenGroupMembersEmpty() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendChatValidationService.validateGroupMembers(1L, List.of()))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_GROUP_MEMBER_REQUIRED")
                );
    }

    @Test
    @DisplayName("그룹 멤버에 자기 자신이 포함되면 실패한다")
    void failWhenGroupMembersContainSelf() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendChatValidationService.validateGroupMembers(1L, List.of(1L, 2L)))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_GROUP_SELF_MEMBER_NOT_ALLOWED")
                );
    }

    @Test
    @DisplayName("그룹 멤버 중 친구가 아닌 사용자가 있으면 전체 실패한다")
    void failWhenGroupMemberIsNotFriend() {
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
    @DisplayName("그룹 멤버 중 차단 관계 사용자가 있으면 전체 실패한다")
    void failWhenGroupMemberIsBlocked() {
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
    @DisplayName("로그인 사용자가 없으면 실패한다")
    void failWhenUnauthenticated() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendChatValidationService.validateDirectTarget(null, 2L))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("UNAUTHORIZED")
                );
    }
}
