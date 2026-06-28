package jp.co.translacat.domain.user.search.service;

import jp.co.translacat.domain.user.entity.User;
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

    public UserSearchResponseDto searchByPublicId(
            Long loginUserId,
            String publicId
    ) {
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
        if (targetUser.getId().equals(loginUserId)) {
            return UserSearchFriendStatus.SELF;
        }

        /*
         * Phase 1.5 후속 이슈에서 FriendRequest/Friend/UserBlock 도메인이 추가되면
         * 아래 상태를 확장한다.
         *
         * - FRIEND
         * - REQUEST_SENT
         * - REQUEST_RECEIVED
         * - BLOCKED
         */

        return UserSearchFriendStatus.NONE;
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
