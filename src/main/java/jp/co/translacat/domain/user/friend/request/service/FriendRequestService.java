package jp.co.translacat.domain.user.friend.request.service;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.friend.request.entity.FriendRequest;
import jp.co.translacat.domain.user.friend.request.repository.FriendRequestRepository;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendRequestService {

    private final FriendRequestRepository friendRequestRepository;

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
    }
}
