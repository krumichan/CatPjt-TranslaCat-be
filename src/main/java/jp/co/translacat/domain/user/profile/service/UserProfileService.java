package jp.co.translacat.domain.user.profile.service;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.profile.dto.UserProfileResponseDto;
import jp.co.translacat.domain.user.profile.dto.UserProfileUpdateRequestDto;
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
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional
    public UserProfile getOrCreateByUser(User user) {
        return userProfileRepository.findByUserAndDeletedFalse(user)
                .orElseGet(() -> userProfileRepository.save(UserProfile.createDefault(user)));
    }

    @Transactional
    public UserProfile getOrCreateByUserId(Long userId) {
        User user = getUser(userId);
        return getOrCreateByUser(user);
    }

    @Transactional
    public UserProfileResponseDto getMyProfile(Long userId) {
        UserProfile userProfile = getOrCreateByUserId(userId);
        return UserProfileResponseDto.from(userProfile);
    }

    @Transactional
    public UserProfileResponseDto updateMyProfile(
            Long userId,
            UserProfileUpdateRequestDto request
    ) {
        UserProfile userProfile = getOrCreateByUserId(userId);
        userProfile.update(
                request.nickname(),
                request.profileImageUrl(),
                request.bio()
        );
        return UserProfileResponseDto.from(userProfile);
    }

    @Transactional
    public UserSummaryProfileResponseDto getSummaryByUser(User user) {
        UserProfile userProfile = getOrCreateByUser(user);
        return UserSummaryProfileResponseDto.from(userProfile);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        "USER_NOT_FOUND"
                ));
    }
}