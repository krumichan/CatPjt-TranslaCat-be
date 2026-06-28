package jp.co.translacat.domain.user.friend.request.service;

import jp.co.translacat.domain.user.block.service.UserBlockService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.friend.request.entity.FriendRequest;
import jp.co.translacat.domain.user.friend.request.enums.FriendRequestStatus;
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
class FriendRequestServiceTest {

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
    @DisplayName("친구 요청 생성 테스트")
    void createPendingRequest() {
        // given
        User requester = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiver = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");

        when(userBlockService.isBlockedBetween(1L, 2L)).thenReturn(false);
        when(friendRequestRepository.existsPendingBetweenUsers(1L, 2L)).thenReturn(false);
        when(friendService.areFriends(1L, 2L)).thenReturn(false);
        when(friendRequestRepository.save(any(FriendRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        FriendRequest friendRequest = friendRequestService.createPendingRequest(requester, receiver);

        // then
        assertThat(friendRequest.getRequesterUser()).isEqualTo(requester);
        assertThat(friendRequest.getReceiverUser()).isEqualTo(receiver);
        assertThat(friendRequest.getStatus()).isEqualTo(FriendRequestStatus.PENDING);

        verify(friendRequestRepository).save(any(FriendRequest.class));
    }

    @Test
    @DisplayName("차단 관계가 있으면 친구 요청을 생성할 수 없다")
    void failWhenBlockedBetweenUsers() {
        // given
        User requester = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiver = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");

        when(userBlockService.isBlockedBetween(1L, 2L)).thenReturn(true);

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendRequestService.createPendingRequest(requester, receiver))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("USER_BLOCKED_BETWEEN")
                );

        verify(friendRequestRepository, never()).save(any(FriendRequest.class));
    }

    @Test
    @DisplayName("중복 PENDING 친구 요청은 생성할 수 없다")
    void failWhenPendingRequestAlreadyExists() {
        // given
        User requester = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiver = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");

        when(userBlockService.isBlockedBetween(1L, 2L)).thenReturn(false);
        when(friendRequestRepository.existsPendingBetweenUsers(1L, 2L)).thenReturn(true);

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendRequestService.createPendingRequest(requester, receiver))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_REQUEST_ALREADY_PENDING")
                );

        verify(friendRequestRepository, never()).save(any(FriendRequest.class));
    }

    @Test
    @DisplayName("이미 친구 관계이면 친구 요청을 생성할 수 없다")
    void failWhenAlreadyFriend() {
        // given
        User requester = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiver = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");

        when(userBlockService.isBlockedBetween(1L, 2L)).thenReturn(false);
        when(friendRequestRepository.existsPendingBetweenUsers(1L, 2L)).thenReturn(false);
        when(friendService.areFriends(1L, 2L)).thenReturn(true);

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendRequestService.createPendingRequest(requester, receiver))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_ALREADY_EXISTS")
                );

        verify(friendRequestRepository, never()).save(any(FriendRequest.class));
    }

    @Test
    @DisplayName("친구 요청 조회 실패 테스트")
    void failWhenFriendRequestDoesNotExist() {
        // given
        Long requestId = 999L;

        when(friendRequestRepository.findByIdAndDeletedFalse(requestId)).thenReturn(Optional.empty());

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendRequestService.getFriendRequest(requestId))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_REQUEST_NOT_FOUND")
                );
    }

    @Test
    @DisplayName("친구 요청 수락 서비스 테스트")
    void accept() {
        // given
        FriendRequest friendRequest = createFriendRequest();

        // when
        FriendRequest result = friendRequestService.accept(friendRequest);

        // then
        assertThat(result.getStatus()).isEqualTo(FriendRequestStatus.ACCEPTED);
        assertThat(result.getRespondedAt()).isNotNull();
    }

    @Test
    @DisplayName("친구 요청 거절 서비스 테스트")
    void reject() {
        // given
        FriendRequest friendRequest = createFriendRequest();

        // when
        FriendRequest result = friendRequestService.reject(friendRequest);

        // then
        assertThat(result.getStatus()).isEqualTo(FriendRequestStatus.REJECTED);
        assertThat(result.getRespondedAt()).isNotNull();
    }

    @Test
    @DisplayName("친구 요청 취소 서비스 테스트")
    void cancel() {
        // given
        FriendRequest friendRequest = createFriendRequest();

        // when
        FriendRequest result = friendRequestService.cancel(friendRequest);

        // then
        assertThat(result.getStatus()).isEqualTo(FriendRequestStatus.CANCELED);
        assertThat(result.getRespondedAt()).isNotNull();
    }

    private FriendRequest createFriendRequest() {
        User requester = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiver = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");
        return FriendRequest.create(requester, receiver);
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
