package jp.co.translacat.domain.user.friend.request.service;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.friend.request.dto.FriendRequestResponseDto;
import jp.co.translacat.domain.user.friend.request.dto.FriendRequestSendRequestDto;
import jp.co.translacat.domain.user.friend.request.entity.FriendRequest;
import jp.co.translacat.domain.user.friend.request.repository.FriendRequestRepository;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;
import jp.co.translacat.domain.user.profile.service.UserProfileService;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendRequestService {

    private final FriendRequestRepository friendRequestRepository;
    private final UserRepository userRepository;
    private final UserProfileService userProfileService;

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

        /*
         * Friend 도메인 구현 후 아래 검증을 추가한다.
         *
         * - 이미 친구인 사용자에게 요청할 수 없다.
         *
         * UserBlock 도메인 구현 후 아래 검증을 추가한다.
         *
         * - 내가 상대를 차단한 경우 요청할 수 없다.
         * - 상대가 나를 차단한 경우 요청할 수 없다.
         */
    }
}
