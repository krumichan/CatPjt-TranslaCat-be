package jp.co.translacat.domain.user.profile.service;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.profile.dto.UserProfileResponseDto;
import jp.co.translacat.domain.user.profile.dto.UserProfileUpdateRequestDto;
import jp.co.translacat.domain.user.profile.entity.UserProfile;
import jp.co.translacat.domain.user.profile.repository.UserProfileRepository;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    @Test
    @DisplayName("사용자별 UserProfile 1개만 생성되는지 테스트")
    void getExistingUserProfileWithoutCreatingNewProfile() {
        // given
        Long userId = 1L;
        User user = createUser(userId, "test@example.com", "testUser", "TCAT-00000001");
        UserProfile existingProfile = UserProfile.createDefault(user);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserAndDeletedFalse(user)).thenReturn(Optional.of(existingProfile));

        // when
        UserProfile profile = userProfileService.getOrCreateByUserId(userId);

        // then
        assertThat(profile).isEqualTo(existingProfile);
        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }

    @Test
    @DisplayName("기존 사용자에 프로필이 없을 때 기본 프로필 생성 테스트")
    void createDefaultProfileWhenUserProfileDoesNotExist() {
        // given
        Long userId = 1L;
        User user = createUser(userId, "test@example.com", "testUser", "TCAT-00000001");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserAndDeletedFalse(user)).thenReturn(Optional.empty());
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UserProfile profile = userProfileService.getOrCreateByUserId(userId);

        // then
        assertThat(profile.getUser()).isEqualTo(user);
        assertThat(profile.getNickname()).isEqualTo("testUser");
        verify(userProfileRepository).save(any(UserProfile.class));
    }

    @Test
    @DisplayName("내 프로필 조회에 publicId 포함 테스트")
    void getMyProfileWithPublicId() {
        // given
        Long userId = 1L;
        User user = createUser(userId, "test@example.com", "testUser", "TCAT-00000001");
        UserProfile existingProfile = UserProfile.createDefault(user);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserAndDeletedFalse(user)).thenReturn(Optional.of(existingProfile));

        // when
        UserProfileResponseDto response = userProfileService.getMyProfile(userId);

        // then
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.publicId()).isEqualTo("TCAT-00000001");
        assertThat(response.nickname()).isEqualTo("testUser");
    }

    @Test
    @DisplayName("내 프로필 수정 테스트")
    void updateMyProfile() {
        // given
        Long userId = 1L;
        User user = createUser(userId, "test@example.com", "testUser", "TCAT-00000001");
        UserProfile existingProfile = UserProfile.createDefault(user);
        UserProfileUpdateRequestDto request = new UserProfileUpdateRequestDto(
                "updatedNickname",
                "https://example.com/profile.png",
                "안녕하세요. TranslaCat을 사용하고 있습니다."
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserAndDeletedFalse(user)).thenReturn(Optional.of(existingProfile));

        // when
        UserProfileResponseDto response = userProfileService.updateMyProfile(userId, request);

        // then
        assertThat(response.nickname()).isEqualTo("updatedNickname");
        assertThat(response.profileImageUrl()).isEqualTo("https://example.com/profile.png");
        assertThat(response.bio()).isEqualTo("안녕하세요. TranslaCat을 사용하고 있습니다.");
    }

    @Test
    @DisplayName("존재하지 않는 사용자 프로필 조회 실패 테스트")
    void failWhenUserDoesNotExist() {
        // given
        Long userId = 999L;

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> userProfileService.getMyProfile(userId))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("USER_NOT_FOUND")
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
