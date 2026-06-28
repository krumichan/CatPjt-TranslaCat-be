package jp.co.translacat.domain.user.friend.request.service;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.friend.request.dto.FriendRequestListItemResponseDto;
import jp.co.translacat.domain.user.friend.request.entity.FriendRequest;
import jp.co.translacat.domain.user.friend.request.enums.FriendRequestStatus;
import jp.co.translacat.domain.user.friend.request.repository.FriendRequestRepository;
import jp.co.translacat.domain.user.friend.service.FriendService;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;
import jp.co.translacat.domain.user.profile.service.UserProfileService;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendRequestManageServiceTest {

    @Mock
    private FriendRequestRepository friendRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private FriendService friendService;

    @InjectMocks
    private FriendRequestService friendRequestService;

    @Test
    @DisplayName("받은 PENDING 친구 요청 목록 조회")
    void getReceivedPendingRequests() {
        // given
        User requester = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiver = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");
        FriendRequest friendRequest = FriendRequest.create(requester, receiver);

        when(friendRequestRepository.findAllByReceiverUserIdAndStatusAndDeletedFalseOrderByRequestedAtDesc(
                2L,
                FriendRequestStatus.PENDING
        )).thenReturn(List.of(friendRequest));
        mockProfiles(requester, receiver);

        // when
        List<FriendRequestListItemResponseDto> responses =
                friendRequestService.getReceivedPendingRequests(2L);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).requester().userId()).isEqualTo(1L);
        assertThat(responses.get(0).receiver().userId()).isEqualTo(2L);
        assertThat(responses.get(0).status()).isEqualTo(FriendRequestStatus.PENDING);
    }

    @Test
    @DisplayName("보낸 PENDING 친구 요청 목록 조회")
    void getSentPendingRequests() {
        // given
        User requester = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiver = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");
        FriendRequest friendRequest = FriendRequest.create(requester, receiver);

        when(friendRequestRepository.findAllByRequesterUserIdAndStatusAndDeletedFalseOrderByRequestedAtDesc(
                1L,
                FriendRequestStatus.PENDING
        )).thenReturn(List.of(friendRequest));
        mockProfiles(requester, receiver);

        // when
        List<FriendRequestListItemResponseDto> responses =
                friendRequestService.getSentPendingRequests(1L);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).requester().userId()).isEqualTo(1L);
        assertThat(responses.get(0).receiver().userId()).isEqualTo(2L);
        assertThat(responses.get(0).status()).isEqualTo(FriendRequestStatus.PENDING);
    }

    @Test
    @DisplayName("친구 요청 수락 시 친구 관계를 생성한다")
    void acceptFriendRequest() {
        // given
        User requester = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiver = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");
        FriendRequest friendRequest = FriendRequest.create(requester, receiver);

        when(friendRequestRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(friendRequest));
        mockProfiles(requester, receiver);

        // when
        FriendRequestListItemResponseDto response = friendRequestService.acceptFriendRequest(2L, 100L);

        // then
        assertThat(response.status()).isEqualTo(FriendRequestStatus.ACCEPTED);
        assertThat(response.respondedAt()).isNotNull();

        verify(friendService).createFriend(requester, receiver);
    }

    @Test
    @DisplayName("친구 요청 수신자가 아니면 수락할 수 없다")
    void failToAcceptWhenNotReceiver() {
        // given
        User requester = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiver = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");
        FriendRequest friendRequest = FriendRequest.create(requester, receiver);

        when(friendRequestRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(friendRequest));

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendRequestService.acceptFriendRequest(1L, 100L))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_REQUEST_NOT_RECEIVER")
                );

        verify(friendService, never()).createFriend(any(User.class), any(User.class));
    }

    @Test
    @DisplayName("친구 요청 거절")
    void rejectFriendRequest() {
        // given
        User requester = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiver = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");
        FriendRequest friendRequest = FriendRequest.create(requester, receiver);

        when(friendRequestRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(friendRequest));
        mockProfiles(requester, receiver);

        // when
        FriendRequestListItemResponseDto response = friendRequestService.rejectFriendRequest(2L, 100L);

        // then
        assertThat(response.status()).isEqualTo(FriendRequestStatus.REJECTED);
        assertThat(response.respondedAt()).isNotNull();

        verify(friendService, never()).createFriend(any(User.class), any(User.class));
    }

    @Test
    @DisplayName("친구 요청 취소")
    void cancelFriendRequest() {
        // given
        User requester = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiver = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");
        FriendRequest friendRequest = FriendRequest.create(requester, receiver);

        when(friendRequestRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(friendRequest));
        mockProfiles(requester, receiver);

        // when
        FriendRequestListItemResponseDto response = friendRequestService.cancelFriendRequest(1L, 100L);

        // then
        assertThat(response.status()).isEqualTo(FriendRequestStatus.CANCELED);
        assertThat(response.respondedAt()).isNotNull();

        verify(friendService, never()).createFriend(any(User.class), any(User.class));
    }

    @Test
    @DisplayName("친구 요청 발신자가 아니면 취소할 수 없다")
    void failToCancelWhenNotRequester() {
        // given
        User requester = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiver = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");
        FriendRequest friendRequest = FriendRequest.create(requester, receiver);

        when(friendRequestRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(friendRequest));

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendRequestService.cancelFriendRequest(2L, 100L))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_REQUEST_NOT_REQUESTER")
                );
    }

    @Test
    @DisplayName("이미 처리된 요청은 수락/거절/취소할 수 없다")
    void failWhenAlreadyProcessedRequest() {
        // given
        User requester = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiver = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");
        FriendRequest friendRequest = FriendRequest.create(requester, receiver);
        friendRequest.accept();

        when(friendRequestRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(friendRequest));

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendRequestService.rejectFriendRequest(2L, 100L))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_REQUEST_ALREADY_PROCESSED")
                );

        verify(friendService, never()).createFriend(any(User.class), any(User.class));
    }

    private void mockProfiles(
            User requester,
            User receiver
    ) {
        when(userProfileService.getSummaryByUser(requester)).thenReturn(new UserSummaryProfileResponseDto(
                requester.getId(),
                requester.getPublicId(),
                requester.getUsername(),
                null
        ));
        when(userProfileService.getSummaryByUser(receiver)).thenReturn(new UserSummaryProfileResponseDto(
                receiver.getId(),
                receiver.getPublicId(),
                receiver.getUsername(),
                null
        ));
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
