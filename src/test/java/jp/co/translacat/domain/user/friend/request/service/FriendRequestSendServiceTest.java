package jp.co.translacat.domain.user.friend.request.service;

import jp.co.translacat.domain.user.block.service.UserBlockService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.friend.request.dto.FriendRequestResponseDto;
import jp.co.translacat.domain.user.friend.request.dto.FriendRequestSendRequestDto;
import jp.co.translacat.domain.user.friend.request.entity.FriendRequest;
import jp.co.translacat.domain.user.friend.request.enums.FriendRequestStatus;
import jp.co.translacat.domain.user.friend.request.repository.FriendRequestRepository;
import jp.co.translacat.domain.user.friend.service.FriendService;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;
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
class FriendRequestSendServiceTest {

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
    @DisplayName("친구 요청 전송 성공")
    void sendFriendRequest() {
        // given
        Long requesterUserId = 1L;
        User requesterUser = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiverUser = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");
        FriendRequestSendRequestDto request = new FriendRequestSendRequestDto("TCAT-00000002");

        UserSummaryProfileResponseDto receiverProfile = createSummary(receiverUser);

        when(userRepository.findById(requesterUserId)).thenReturn(Optional.of(requesterUser));
        when(userRepository.findByPublicId("TCAT-00000002")).thenReturn(Optional.of(receiverUser));
        when(userBlockService.isBlockedBetween(1L, 2L)).thenReturn(false);
        when(friendRequestRepository.existsPendingBetweenUsers(1L, 2L)).thenReturn(false);
        when(friendService.areFriends(1L, 2L)).thenReturn(false);
        when(friendRequestRepository.save(any(FriendRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userProfileQueryService.getSummaryByUser(receiverUser)).thenReturn(receiverProfile);

        // when
        FriendRequestResponseDto response = friendRequestService.sendFriendRequest(
                requesterUserId,
                request
        );

        // then
        assertThat(response.requesterUserId()).isEqualTo(1L);
        assertThat(response.receiverUserId()).isEqualTo(2L);
        assertThat(response.receiverPublicId()).isEqualTo("TCAT-00000002");
        assertThat(response.receiverNickname()).isEqualTo("receiver");
        assertThat(response.status()).isEqualTo(FriendRequestStatus.PENDING);
        assertThat(response.requestedAt()).isNotNull();
        assertThat(response.respondedAt()).isNull();

        verify(friendRequestRepository).save(any(FriendRequest.class));
    }

    @Test
    @DisplayName("존재하지 않는 receiverPublicId는 실패")
    void failWhenReceiverPublicIdDoesNotExist() {
        // given
        Long requesterUserId = 1L;
        User requesterUser = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        FriendRequestSendRequestDto request = new FriendRequestSendRequestDto("UNKNOWN");

        when(userRepository.findById(requesterUserId)).thenReturn(Optional.of(requesterUser));
        when(userRepository.findByPublicId("UNKNOWN")).thenReturn(Optional.empty());

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendRequestService.sendFriendRequest(requesterUserId, request))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("PUBLIC_ID_NOT_FOUND")
                );

        verify(friendRequestRepository, never()).save(any(FriendRequest.class));
    }

    @Test
    @DisplayName("자기 자신에게 친구 요청할 수 없다")
    void failWhenSendRequestToSelf() {
        // given
        Long requesterUserId = 1L;
        User requesterUser = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        FriendRequestSendRequestDto request = new FriendRequestSendRequestDto("TCAT-00000001");

        when(userRepository.findById(requesterUserId)).thenReturn(Optional.of(requesterUser));
        when(userRepository.findByPublicId("TCAT-00000001")).thenReturn(Optional.of(requesterUser));

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendRequestService.sendFriendRequest(requesterUserId, request))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_REQUEST_SELF_NOT_ALLOWED")
                );

        verify(friendRequestRepository, never()).save(any(FriendRequest.class));
    }

    @Test
    @DisplayName("차단 관계가 있으면 친구 요청을 전송할 수 없다")
    void failWhenBlockedBetweenUsers() {
        // given
        Long requesterUserId = 1L;
        User requesterUser = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiverUser = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");
        FriendRequestSendRequestDto request = new FriendRequestSendRequestDto("TCAT-00000002");

        when(userRepository.findById(requesterUserId)).thenReturn(Optional.of(requesterUser));
        when(userRepository.findByPublicId("TCAT-00000002")).thenReturn(Optional.of(receiverUser));
        when(userBlockService.isBlockedBetween(1L, 2L)).thenReturn(true);

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendRequestService.sendFriendRequest(requesterUserId, request))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("USER_BLOCKED_BETWEEN")
                );

        verify(friendRequestRepository, never()).save(any(FriendRequest.class));
    }

    @Test
    @DisplayName("이미 PENDING 요청이 있으면 중복 전송할 수 없다")
    void failWhenPendingRequestAlreadyExists() {
        // given
        Long requesterUserId = 1L;
        User requesterUser = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiverUser = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");
        FriendRequestSendRequestDto request = new FriendRequestSendRequestDto("TCAT-00000002");

        when(userRepository.findById(requesterUserId)).thenReturn(Optional.of(requesterUser));
        when(userRepository.findByPublicId("TCAT-00000002")).thenReturn(Optional.of(receiverUser));
        when(userBlockService.isBlockedBetween(1L, 2L)).thenReturn(false);
        when(friendRequestRepository.existsPendingBetweenUsers(1L, 2L)).thenReturn(true);

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendRequestService.sendFriendRequest(requesterUserId, request))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_REQUEST_ALREADY_PENDING")
                );

        verify(friendRequestRepository, never()).save(any(FriendRequest.class));
    }

    @Test
    @DisplayName("이미 친구 관계이면 친구 요청을 전송할 수 없다")
    void failWhenAlreadyFriend() {
        // given
        Long requesterUserId = 1L;
        User requesterUser = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiverUser = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");
        FriendRequestSendRequestDto request = new FriendRequestSendRequestDto("TCAT-00000002");

        when(userRepository.findById(requesterUserId)).thenReturn(Optional.of(requesterUser));
        when(userRepository.findByPublicId("TCAT-00000002")).thenReturn(Optional.of(receiverUser));
        when(userBlockService.isBlockedBetween(1L, 2L)).thenReturn(false);
        when(friendRequestRepository.existsPendingBetweenUsers(1L, 2L)).thenReturn(false);
        when(friendService.areFriends(1L, 2L)).thenReturn(true);

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendRequestService.sendFriendRequest(requesterUserId, request))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_ALREADY_EXISTS")
                );

        verify(friendRequestRepository, never()).save(any(FriendRequest.class));
    }

    private UserSummaryProfileResponseDto createSummary(User user) {
        return new UserSummaryProfileResponseDto(
                user.getId(),
                user.getPublicId(),
                user.getUsername(),
                null
        );
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
