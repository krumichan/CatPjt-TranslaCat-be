package jp.co.translacat.domain.user.friend.request.service;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.friend.service.FriendService;
import jp.co.translacat.domain.user.friend.request.dto.FriendRequestListItemResponseDto;
import jp.co.translacat.domain.user.friend.request.dto.FriendRequestResponseDto;
import jp.co.translacat.domain.user.friend.request.dto.FriendRequestSendRequestDto;
import jp.co.translacat.domain.user.friend.request.entity.FriendRequest;
import jp.co.translacat.domain.user.friend.request.enums.FriendRequestStatus;
import jp.co.translacat.domain.user.friend.request.repository.FriendRequestRepository;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;
import jp.co.translacat.domain.user.profile.service.UserProfileService;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendRequestService {

    private final FriendRequestRepository friendRequestRepository;
    private final UserRepository userRepository;
    private final UserProfileService userProfileService;
    private final FriendService friendService;

    @Transactional
    public FriendRequestResponseDto sendFriendRequest(
            Long requesterUserId,
            FriendRequestSendRequestDto request
    ) {
        validateSendRequest(request);

        User requesterUser = getUser(requesterUserId);
        User receiverUser = getUserByPublicId(request.receiverPublicId());

        FriendRequest friendRequest = createPendingRequest(
                requesterUser,
                receiverUser
        );

        UserSummaryProfileResponseDto receiverProfile = userProfileService.getSummaryByUser(receiverUser);

        return FriendRequestResponseDto.of(
                friendRequest,
                receiverProfile
        );
    }

    public List<FriendRequestListItemResponseDto> getReceivedPendingRequests(Long loginUserId) {
        return friendRequestRepository
                .findAllByReceiverUserIdAndStatusAndDeletedFalseOrderByRequestedAtDesc(
                        loginUserId,
                        FriendRequestStatus.PENDING
                )
                .stream()
                .map(this::toListItemResponse)
                .toList();
    }

    public List<FriendRequestListItemResponseDto> getSentPendingRequests(Long loginUserId) {
        return friendRequestRepository
                .findAllByRequesterUserIdAndStatusAndDeletedFalseOrderByRequestedAtDesc(
                        loginUserId,
                        FriendRequestStatus.PENDING
                )
                .stream()
                .map(this::toListItemResponse)
                .toList();
    }

    @Transactional
    public FriendRequestListItemResponseDto acceptFriendRequest(
            Long loginUserId,
            Long requestId
    ) {
        FriendRequest friendRequest = getFriendRequest(requestId);
        validateReceiver(friendRequest, loginUserId);

        friendRequest.accept();

        friendService.createFriend(
                friendRequest.getRequesterUser(),
                friendRequest.getReceiverUser()
        );

        return toListItemResponse(friendRequest);
    }

    @Transactional
    public FriendRequestListItemResponseDto rejectFriendRequest(
            Long loginUserId,
            Long requestId
    ) {
        FriendRequest friendRequest = getFriendRequest(requestId);
        validateReceiver(friendRequest, loginUserId);

        friendRequest.reject();

        return toListItemResponse(friendRequest);
    }

    @Transactional
    public FriendRequestListItemResponseDto cancelFriendRequest(
            Long loginUserId,
            Long requestId
    ) {
        FriendRequest friendRequest = getFriendRequest(requestId);
        validateRequester(friendRequest, loginUserId);

        friendRequest.cancel();

        return toListItemResponse(friendRequest);
    }

    @Transactional
    public FriendRequest createPendingRequest(
            User requesterUser,
            User receiverUser
    ) {
        validateDuplicatePendingRequest(requesterUser, receiverUser);

        FriendRequest friendRequest = FriendRequest.create(
                requesterUser,
                receiverUser
        );

        return friendRequestRepository.save(friendRequest);
    }

    public FriendRequest getFriendRequest(Long requestId) {
        return friendRequestRepository.findByIdAndDeletedFalse(requestId)
                .orElseThrow(() -> new BusinessException(
                        "친구 요청을 찾을 수 없습니다.",
                        "FRIEND_REQUEST_NOT_FOUND"
                ));
    }

    @Transactional
    public FriendRequest accept(FriendRequest friendRequest) {
        friendRequest.accept();
        return friendRequest;
    }

    @Transactional
    public FriendRequest reject(FriendRequest friendRequest) {
        friendRequest.reject();
        return friendRequest;
    }

    @Transactional
    public FriendRequest cancel(FriendRequest friendRequest) {
        friendRequest.cancel();
        return friendRequest;
    }

    private FriendRequestListItemResponseDto toListItemResponse(FriendRequest friendRequest) {
        UserSummaryProfileResponseDto requesterProfile =
                userProfileService.getSummaryByUser(friendRequest.getRequesterUser());
        UserSummaryProfileResponseDto receiverProfile =
                userProfileService.getSummaryByUser(friendRequest.getReceiverUser());

        return FriendRequestListItemResponseDto.of(
                friendRequest,
                requesterProfile,
                receiverProfile
        );
    }

    private void validateReceiver(
            FriendRequest friendRequest,
            Long loginUserId
    ) {
        if (!friendRequest.isReceivedBy(loginUserId)) {
            throw new BusinessException(
                    "친구 요청 수신자만 처리할 수 있습니다.",
                    "FRIEND_REQUEST_NOT_RECEIVER"
            );
        }
    }

    private void validateRequester(
            FriendRequest friendRequest,
            Long loginUserId
    ) {
        if (!friendRequest.isRequestedBy(loginUserId)) {
            throw new BusinessException(
                    "친구 요청 발신자만 취소할 수 있습니다.",
                    "FRIEND_REQUEST_NOT_REQUESTER"
            );
        }
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        "USER_NOT_FOUND"
                ));
    }

    private User getUserByPublicId(String publicId) {
        return userRepository.findByPublicId(publicId.trim())
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        "PUBLIC_ID_NOT_FOUND"
                ));
    }

    private void validateSendRequest(FriendRequestSendRequestDto request) {
        if (request == null || request.receiverPublicId() == null || request.receiverPublicId().trim().isEmpty()) {
            throw new BusinessException(
                    "친구 요청 대상 publicId는 필수입니다.",
                    "RECEIVER_PUBLIC_ID_REQUIRED"
            );
        }
    }

    private void validateDuplicatePendingRequest(
            User requesterUser,
            User receiverUser
    ) {
        if (requesterUser == null || receiverUser == null) {
            throw new BusinessException(
                    "친구 요청 사용자를 찾을 수 없습니다.",
                    "USER_NOT_FOUND"
            );
        }

        if (requesterUser.getId() == null || receiverUser.getId() == null) {
            return;
        }

        if (friendRequestRepository.existsPendingBetweenUsers(
                requesterUser.getId(),
                receiverUser.getId()
        )) {
            throw new BusinessException(
                    "이미 대기 중인 친구 요청이 있습니다.",
                    "FRIEND_REQUEST_ALREADY_PENDING"
            );
        }

        if (friendService != null && friendService.areFriends(
                requesterUser.getId(),
                receiverUser.getId()
        )) {
            throw new BusinessException(
                    "이미 친구 관계입니다.",
                    "FRIEND_ALREADY_EXISTS"
            );
        }

        /*
         * UserBlock 도메인 구현 후 아래 검증을 추가한다.
         *
         * - 내가 상대를 차단한 경우 요청할 수 없다.
         * - 상대가 나를 차단한 경우 요청할 수 없다.
         */
    }
}
