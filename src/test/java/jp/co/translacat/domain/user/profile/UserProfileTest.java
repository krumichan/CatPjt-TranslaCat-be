package jp.co.translacat.domain.user.profile;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.profile.dto.UserProfileResponseDto;
import jp.co.translacat.domain.user.profile.entity.UserProfile;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

class UserProfileTest {

    @Test
    @DisplayName("UserProfile 생성 테스트")
    void createDefaultProfile() {
        // given
        User user = createUser(1L, "test@example.com", "testUser", "TCAT-00000001");

        // when
        UserProfile userProfile = UserProfile.createDefault(user);

        // then
        assertThat(userProfile.getUser()).isEqualTo(user);
        assertThat(userProfile.getNickname()).isEqualTo("testUser");
        assertThat(userProfile.getProfileImageUrl()).isNull();
        assertThat(userProfile.getBio()).isNull();
        assertThat(userProfile.isDeleted()).isFalse();
        assertThat(userProfile.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("nickname 수정 테스트")
    void updateNickname() {
        // given
        UserProfile userProfile = UserProfile.createDefault(
                createUser(1L, "test@example.com", "testUser", "TCAT-00000001")
        );

        // when
        userProfile.update("updatedNickname", null, null);

        // then
        assertThat(userProfile.getNickname()).isEqualTo("updatedNickname");
    }

    @Test
    @DisplayName("profileImageUrl 수정 테스트")
    void updateProfileImageUrl() {
        // given
        UserProfile userProfile = UserProfile.createDefault(
                createUser(1L, "test@example.com", "testUser", "TCAT-00000001")
        );

        // when
        userProfile.update("testUser", "https://example.com/profile.png", null);

        // then
        assertThat(userProfile.getProfileImageUrl()).isEqualTo("https://example.com/profile.png");
    }

    @Test
    @DisplayName("bio 수정 테스트")
    void updateBio() {
        // given
        UserProfile userProfile = UserProfile.createDefault(
                createUser(1L, "test@example.com", "testUser", "TCAT-00000001")
        );

        // when
        userProfile.update("testUser", null, "안녕하세요. TranslaCat을 사용하고 있습니다.");

        // then
        assertThat(userProfile.getBio()).isEqualTo("안녕하세요. TranslaCat을 사용하고 있습니다.");
    }

    @Test
    @DisplayName("publicId가 UserProfile에 중복 저장되지 않는지 확인")
    void publicIdIsNotDuplicatedInUserProfile() {
        // given
        User user = createUser(1L, "test@example.com", "testUser", "TCAT-00000001");
        UserProfile userProfile = UserProfile.createDefault(user);

        // when
        String[] userProfileFieldNames = Arrays.stream(UserProfile.class.getDeclaredFields())
                .map(Field::getName)
                .toArray(String[]::new);
        UserProfileResponseDto response = UserProfileResponseDto.from(userProfile);

        // then
        assertThat(userProfileFieldNames).doesNotContain("publicId");
        assertThat(response.publicId()).isEqualTo("TCAT-00000001");
    }

    @Test
    @DisplayName("nickname 검증 실패 테스트")
    void failWhenNicknameIsBlank() {
        // given
        UserProfile userProfile = UserProfile.createDefault(
                createUser(1L, "test@example.com", "testUser", "TCAT-00000001")
        );

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> userProfile.update("   ", null, null))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("USER_PROFILE_NICKNAME_REQUIRED")
                );
    }

    @Test
    @DisplayName("bio 길이 초과 테스트")
    void failWhenBioIsTooLong() {
        // given
        UserProfile userProfile = UserProfile.createDefault(
                createUser(1L, "test@example.com", "testUser", "TCAT-00000001")
        );
        String tooLongBio = "a".repeat(UserProfile.BIO_MAX_LENGTH + 1);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> userProfile.update("testUser", null, tooLongBio))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("USER_PROFILE_BIO_TOO_LONG")
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
