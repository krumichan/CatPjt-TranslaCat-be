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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class UserProfileTest {

    @Test
    @DisplayName("UserProfile 생성 테스트")
    void createDefaultProfile() {
        User user = createUser(
                1L,
                "test@example.com",
                "testUser",
                "TCAT-00000001"
        );

        UserProfile userProfile =
                UserProfile.createDefault(user);

        assertThat(userProfile.getUser()).isEqualTo(user);
        assertThat(userProfile.getNickname())
                .isEqualTo("testUser");
        assertThat(userProfile.getProfileImageObjectKey())
                .isNull();
        assertThat(
                userProfile.getProfileBackgroundImageObjectKey()
        ).isNull();
        assertThat(userProfile.getBio()).isNull();
        assertThat(userProfile.isDeleted()).isFalse();
        assertThat(userProfile.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("nickname 수정 테스트")
    void updateNickname() {
        UserProfile userProfile =
                UserProfile.createDefault(
                        createUser(
                                1L,
                                "test@example.com",
                                "testUser",
                                "TCAT-00000001"
                        )
                );

        userProfile.updateText(
                "updatedNickname",
                null
        );

        assertThat(userProfile.getNickname())
                .isEqualTo("updatedNickname");
    }

    @Test
    @DisplayName("프로필 이미지 object key 교체 테스트")
    void replaceProfileImageObjectKey() {
        UserProfile userProfile =
                UserProfile.createDefault(
                        createUser(
                                1L,
                                "test@example.com",
                                "testUser",
                                "TCAT-00000001"
                        )
                );

        String firstKey =
                "user-profile/1/profile/first.webp";

        String secondKey =
                "user-profile/1/profile/second.webp";

        String oldAtFirst =
                userProfile.replaceProfileImageObjectKey(firstKey);

        String oldAtSecond =
                userProfile.replaceProfileImageObjectKey(secondKey);

        assertThat(oldAtFirst).isNull();
        assertThat(oldAtSecond).isEqualTo(firstKey);
        assertThat(userProfile.getProfileImageObjectKey())
                .isEqualTo(secondKey);
    }

    @Test
    @DisplayName("프로필 배경 이미지 object key 교체 테스트")
    void replaceProfileBackgroundImageObjectKey() {
        UserProfile userProfile =
                UserProfile.createDefault(
                        createUser(
                                1L,
                                "test@example.com",
                                "testUser",
                                "TCAT-00000001"
                        )
                );

        String objectKey =
                "user-profile/1/background/background.jpg";

        String oldObjectKey =
                userProfile.replaceProfileBackgroundImageObjectKey(
                        objectKey
                );

        assertThat(oldObjectKey).isNull();

        assertThat(
                userProfile.getProfileBackgroundImageObjectKey()
        ).isEqualTo(objectKey);
    }

    @Test
    @DisplayName("bio 수정 테스트")
    void updateBio() {
        UserProfile userProfile =
                UserProfile.createDefault(
                        createUser(
                                1L,
                                "test@example.com",
                                "testUser",
                                "TCAT-00000001"
                        )
                );

        userProfile.updateText(
                "testUser",
                "안녕하세요. TranslaCat을 사용하고 있습니다."
        );

        assertThat(userProfile.getBio())
                .isEqualTo(
                        "안녕하세요. TranslaCat을 사용하고 있습니다."
                );
    }

    @Test
    @DisplayName("publicId가 UserProfile에 중복 저장되지 않는지 확인")
    void publicIdIsNotDuplicatedInUserProfile() {
        User user = createUser(
                1L,
                "test@example.com",
                "testUser",
                "TCAT-00000001"
        );

        UserProfile userProfile =
                UserProfile.createDefault(user);

        String[] userProfileFieldNames =
                Arrays.stream(
                                UserProfile.class.getDeclaredFields()
                        )
                        .map(Field::getName)
                        .toArray(String[]::new);

        UserProfileResponseDto response =
                UserProfileResponseDto.from(
                        userProfile,
                        null,
                        null
                );

        assertThat(userProfileFieldNames)
                .doesNotContain(
                        "publicId",
                        "profileImageUrl"
                );

        assertThat(response.publicId())
                .isEqualTo("TCAT-00000001");
    }

    @Test
    @DisplayName("nickname 검증 실패 테스트")
    void failWhenNicknameIsBlank() {
        UserProfile userProfile =
                UserProfile.createDefault(
                        createUser(
                                1L,
                                "test@example.com",
                                "testUser",
                                "TCAT-00000001"
                        )
                );

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(
                        () -> userProfile.updateText(
                                "   ",
                                null
                        )
                )
                .satisfies(exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        "USER_PROFILE_NICKNAME_REQUIRED"
                                )
                );
    }

    @Test
    @DisplayName("bio 길이 초과 테스트")
    void failWhenBioIsTooLong() {
        UserProfile userProfile =
                UserProfile.createDefault(
                        createUser(
                                1L,
                                "test@example.com",
                                "testUser",
                                "TCAT-00000001"
                        )
                );

        String tooLongBio =
                "a".repeat(UserProfile.BIO_MAX_LENGTH + 1);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(
                        () -> userProfile.updateText(
                                "testUser",
                                tooLongBio
                        )
                )
                .satisfies(exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        "USER_PROFILE_BIO_TOO_LONG"
                                )
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
