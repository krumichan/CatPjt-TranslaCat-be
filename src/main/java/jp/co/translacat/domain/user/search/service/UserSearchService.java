package jp.co.translacat.domain.user.search.service;

import jp.co.translacat.domain.user.block.service.UserBlockService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.friend.request.entity.FriendRequest;
import jp.co.translacat.domain.user.friend.request.enums.FriendRequestStatus;
import jp.co.translacat.domain.user.friend.request.repository.FriendRequestRepository;
import jp.co.translacat.domain.user.friend.service.FriendService;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;
import jp.co.translacat.domain.user.profile.service.UserProfileQueryService;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.domain.user.search.dto.UserSearchResponseDto;
import jp.co.translacat.domain.user.search.enums.UserSearchFriendStatus;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSearchService {

    private final UserRepository userRepository;
    private final UserProfileQueryService userProfileQueryService;
    private final FriendService friendService;
    private final FriendRequestRepository friendRequestRepository;
    private final UserBlockService userBlockService;

    public UserSearchResponseDto searchByPublicId(
            Long loginUserId,
            String publicId
    ) {
        validateLoginUser(loginUserId);
        validatePublicId(publicId);

        User targetUser = userRepository.findByPublicId(publicId.trim())
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        "PUBLIC_ID_NOT_FOUND"
                ));

        UserSummaryProfileResponseDto profile = userProfileQueryService.getSummaryByUser(targetUser);
        UserSearchFriendStatus friendStatus = resolveFriendStatus(loginUserId, targetUser);

        return UserSearchResponseDto.of(
                profile,
                friendStatus
        );
    }

    private UserSearchFriendStatus resolveFriendStatus(
            Long loginUserId,
            User targetUser
    ) {
        Long targetUserId = targetUser.getId();

        if (targetUserId.equals(loginUserId)) {
            return UserSearchFriendStatus.SELF;
        }

        if (userBlockService.isBlockedBetween(loginUserId, targetUserId)) {
            return UserSearchFriendStatus.BLOCKED;
        }

        if (friendService.areFriends(loginUserId, targetUserId)) {
            return UserSearchFriendStatus.FRIEND;
        }

        return friendRequestRepository.findBetweenUsersByStatus(
                        loginUserId,
                        targetUserId,
                        FriendRequestStatus.PENDING
                )
                .map(friendRequest -> resolvePendingRequestStatus(
                        friendRequest,
                        loginUserId
                ))
                .orElse(UserSearchFriendStatus.NONE);
    }

    private UserSearchFriendStatus resolvePendingRequestStatus(
            FriendRequest friendRequest,
            Long loginUserId
    ) {
        if (friendRequest.isRequestedBy(loginUserId)) {
            return UserSearchFriendStatus.REQUEST_SENT;
        }

        if (friendRequest.isReceivedBy(loginUserId)) {
            return UserSearchFriendStatus.REQUEST_RECEIVED;
        }

        return UserSearchFriendStatus.NONE;
    }

    private void validateLoginUser(Long loginUserId) {
        if (loginUserId == null) {
            throw new BusinessException(
                    "로그인이 필요합니다.",
                    "UNAUTHORIZED"
            );
        }
    }

    private void validatePublicId(String publicId) {
        if (publicId == null || publicId.trim().isEmpty()) {
            throw new BusinessException(
                    "publicId는 필수입니다.",
                    "PUBLIC_ID_REQUIRED"
            );
        }
    }
}
