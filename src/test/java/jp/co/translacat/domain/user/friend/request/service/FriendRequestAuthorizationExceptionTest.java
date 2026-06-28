package jp.co.translacat.domain.user.friend.request.service;

import jp.co.translacat.domain.user.block.service.UserBlockService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.friend.request.entity.FriendRequest;
import jp.co.translacat.domain.user.friend.request.repository.FriendRequestRepository;
import jp.co.translacat.domain.user.friend.service.FriendService;
import jp.co.translacat.domain.user.profile.service.UserProfileQueryService;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendRequestAuthorizationExceptionTest {

    @Mock
    private FriendRequestRepository friendRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileQueryService userProfileQueryService;

    @Mock
    private FriendService friendService;

    @Mock
    private UserBlockService userBlockService;

    @InjectMocks
    private FriendRequestService friendRequestService;

    @Test
    @DisplayName("친구 요청 수신자가 아니면 수락할 수 없다")
    void failToAcceptWhenLoginUserIsNotReceiver() {
        // given
        User requester = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiver = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");
        User otherUser = createUser(3L, "other@example.com", "other", "TCAT-00000003");

        FriendRequest friendRequest = FriendRequest.create(requester, receiver);

        when(friendRequestRepository.findByIdAndDeletedFalse(100L))
                .thenReturn(Optional.of(friendRequest));

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendRequestService.acceptFriendRequest(otherUser.getId(), 100L))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_REQUEST_NOT_RECEIVER")
                );

        verify(friendService, never()).createFriend(any(User.class), any(User.class));
    }

    @Test
    @DisplayName("친구 요청 수신자가 아니면 거절할 수 없다")
    void failToRejectWhenLoginUserIsNotReceiver() {
        // given
        User requester = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiver = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");
        User otherUser = createUser(3L, "other@example.com", "other", "TCAT-00000003");

        FriendRequest friendRequest = FriendRequest.create(requester, receiver);

        when(friendRequestRepository.findByIdAndDeletedFalse(100L))
                .thenReturn(Optional.of(friendRequest));

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendRequestService.rejectFriendRequest(otherUser.getId(), 100L))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_REQUEST_NOT_RECEIVER")
                );

        verify(friendService, never()).createFriend(any(User.class), any(User.class));
    }

    @Test
    @DisplayName("친구 요청 발신자가 아니면 취소할 수 없다")
    void failToCancelWhenLoginUserIsNotRequester() {
        // given
        User requester = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiver = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");
        User otherUser = createUser(3L, "other@example.com", "other", "TCAT-00000003");

        FriendRequest friendRequest = FriendRequest.create(requester, receiver);

        when(friendRequestRepository.findByIdAndDeletedFalse(100L))
                .thenReturn(Optional.of(friendRequest));

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendRequestService.cancelFriendRequest(otherUser.getId(), 100L))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_REQUEST_NOT_REQUESTER")
                );

        verify(friendService, never()).createFriend(any(User.class), any(User.class));
    }

    @Test
    @DisplayName("이미 처리된 친구 요청은 다시 수락할 수 없다")
    void failToAcceptAlreadyProcessedRequest() {
        // given
        User requester = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiver = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");

        FriendRequest friendRequest = FriendRequest.create(requester, receiver);
        friendRequest.reject();

        when(friendRequestRepository.findByIdAndDeletedFalse(100L))
                .thenReturn(Optional.of(friendRequest));

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendRequestService.acceptFriendRequest(receiver.getId(), 100L))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_REQUEST_ALREADY_PROCESSED")
                );

        verify(friendService, never()).createFriend(any(User.class), any(User.class));
    }

    @Test
    @DisplayName("존재하지 않는 친구 요청은 처리할 수 없다")
    void failWhenFriendRequestDoesNotExist() {
        // given
        when(friendRequestRepository.findByIdAndDeletedFalse(999L))
                .thenReturn(Optional.empty());

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendRequestService.acceptFriendRequest(2L, 999L))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_REQUEST_NOT_FOUND")
                );

        verify(friendService, never()).createFriend(any(User.class), any(User.class));
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
        user.setId(id);
        return user;
    }
}
