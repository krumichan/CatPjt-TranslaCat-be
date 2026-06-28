package jp.co.translacat.domain.user.profile.service;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;
import jp.co.translacat.domain.user.profile.entity.UserProfile;
import jp.co.translacat.domain.user.profile.repository.UserProfileRepository;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileQueryService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    public UserSummaryProfileResponseDto getSummaryByUser(User user) {
        validateUser(user);

        UserProfile userProfile = userProfileRepository.findByUserAndDeletedFalse(user)
                .orElseGet(() -> UserProfile.createDefault(user));

        return UserSummaryProfileResponseDto.from(userProfile);
    }

    public UserSummaryProfileResponseDto getSummaryByUserId(Long userId) {
        User user = getUser(userId);
        return getSummaryByUser(user);
    }

    public UserSummaryProfileResponseDto getSummaryByPublicId(String publicId) {
        validatePublicId(publicId);

        User user = userRepository.findByPublicId(publicId.trim())
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        "PUBLIC_ID_NOT_FOUND"
                ));

        return getSummaryByUser(user);
    }

    private User getUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(
                    "사용자 ID는 필수입니다.",
                    "USER_ID_REQUIRED"
            );
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        "USER_NOT_FOUND"
                ));
    }

    private void validateUser(User user) {
        if (user == null) {
            throw new BusinessException(
                    "사용자를 찾을 수 없습니다.",
                    "USER_NOT_FOUND"
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
