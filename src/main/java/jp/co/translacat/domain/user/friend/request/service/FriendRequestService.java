package jp.co.translacat.domain.user.friend.request.service;

import jp.co.translacat.domain.user.block.service.UserBlockService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.friend.request.dto.FriendRequestListItemResponseDto;
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
    private final UserProfileQueryService userProfileQueryService;
    private final FriendService friendService;
    private final UserBlockService userBlockService;

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

        UserSummaryProfileResponseDto receiverProfile =
                userProfileQueryService.getSummaryByUser(receiverUser);

        return FriendRequestResponseDto.of(
                friendRequest,
                receiverProfile
        );
    }

    @Transactional
    public FriendRequest createPendingRequest(
            User requesterUser,
            User receiverUser
    ) {
        validateUsers(requesterUser, receiverUser);
        validateSelfRequest(requesterUser, receiverUser);
        validateBlockRelation(requesterUser, receiverUser);
        validateDuplicatePendingRequest(requesterUser, receiverUser);
        validateAlreadyFriend(requesterUser, receiverUser);

        FriendRequest friendRequest = FriendRequest.create(
                requesterUser,
                receiverUser
        );

        return friendRequestRepository.save(friendRequest);
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

        validateReceiver(
                friendRequest,
                loginUserId
        );

        FriendRequest acceptedRequest = accept(friendRequest);

        friendService.createFriend(
                acceptedRequest.getRequesterUser(),
                acceptedRequest.getReceiverUser()
        );

        return toListItemResponse(acceptedRequest);
    }

    @Transactional
    public FriendRequestListItemResponseDto rejectFriendRequest(
            Long loginUserId,
            Long requestId
    ) {
        FriendRequest friendRequest = getFriendRequest(requestId);

        validateReceiver(
                friendRequest,
                loginUserId
        );

        return toListItemResponse(reject(friendRequest));
    }

    @Transactional
    public FriendRequestListItemResponseDto cancelFriendRequest(
            Long loginUserId,
            Long requestId
    ) {
        FriendRequest friendRequest = getFriendRequest(requestId);

        validateRequester(
                friendRequest,
                loginUserId
        );

        return toListItemResponse(cancel(friendRequest));
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
                userProfileQueryService.getSummaryByUser(friendRequest.getRequesterUser());

        UserSummaryProfileResponseDto receiverProfile =
                userProfileQueryService.getSummaryByUser(friendRequest.getReceiverUser());

        return FriendRequestListItemResponseDto.of(
                friendRequest,
                requesterProfile,
                receiverProfile
        );
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
        if (request == null
                || request.receiverPublicId() == null
                || request.receiverPublicId().trim().isEmpty()) {
            throw new BusinessException(
                    "친구 요청 대상 publicId는 필수입니다.",
                    "RECEIVER_PUBLIC_ID_REQUIRED"
            );
        }
    }

    private void validateUsers(
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
            throw new BusinessException(
                    "친구 요청 사용자를 찾을 수 없습니다.",
                    "USER_NOT_FOUND"
            );
        }
    }

    private void validateSelfRequest(
            User requesterUser,
            User receiverUser
    ) {
        if (requesterUser.getId().equals(receiverUser.getId())) {
            throw new BusinessException(
                    "자기 자신에게 친구 요청을 보낼 수 없습니다.",
                    "FRIEND_REQUEST_SELF_NOT_ALLOWED"
            );
        }
    }

    private void validateBlockRelation(
            User requesterUser,
            User receiverUser
    ) {
        if (userBlockService.isBlockedBetween(
                requesterUser.getId(),
                receiverUser.getId()
        )) {
            throw new BusinessException(
                    "차단 관계가 있는 사용자에게 친구 요청을 보낼 수 없습니다.",
                    "USER_BLOCKED_BETWEEN"
            );
        }
    }

    private void validateDuplicatePendingRequest(
            User requesterUser,
            User receiverUser
    ) {
        if (friendRequestRepository.existsPendingBetweenUsers(
                requesterUser.getId(),
                receiverUser.getId()
        )) {
            throw new BusinessException(
                    "이미 대기 중인 친구 요청이 있습니다.",
                    "FRIEND_REQUEST_ALREADY_PENDING"
            );
        }
    }

    private void validateAlreadyFriend(
            User requesterUser,
            User receiverUser
    ) {
        if (friendService.areFriends(
                requesterUser.getId(),
                receiverUser.getId()
        )) {
            throw new BusinessException(
                    "이미 친구 관계인 사용자입니다.",
                    "FRIEND_ALREADY_EXISTS"
            );
        }
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
}
