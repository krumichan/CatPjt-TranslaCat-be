package jp.co.translacat.domain.user.profile.service;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private UserProfileQueryService userProfileQueryService;

    @Test
    @DisplayName("사용자 요약 프로필 조회 테스트")
    void getSummaryByUser() {
        // given
        User user = createUser(1L, "user@example.com", "user", "TCAT-00000001");
        UserProfile userProfile = UserProfile.createDefault(user);

        when(userProfileRepository.findByUserAndDeletedFalse(user)).thenReturn(Optional.of(userProfile));

        // when
        UserSummaryProfileResponseDto response = userProfileQueryService.getSummaryByUser(user);

        // then
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.publicId()).isEqualTo("TCAT-00000001");
        assertThat(response.nickname()).isEqualTo("user");
        assertThat(response.profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("프로필이 없으면 저장 없이 기본 요약 프로필을 반환한다")
    void getSummaryByUserWithoutProfile() {
        // given
        User user = createUser(1L, "user@example.com", "user", "TCAT-00000001");

        when(userProfileRepository.findByUserAndDeletedFalse(user)).thenReturn(Optional.empty());

        // when
        UserSummaryProfileResponseDto response = userProfileQueryService.getSummaryByUser(user);

        // then
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.publicId()).isEqualTo("TCAT-00000001");
        assertThat(response.nickname()).isEqualTo("user");

        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }

    @Test
    @DisplayName("userId 기반 사용자 요약 프로필 조회 테스트")
    void getSummaryByUserId() {
        // given
        User user = createUser(1L, "user@example.com", "user", "TCAT-00000001");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserAndDeletedFalse(user)).thenReturn(Optional.empty());

        // when
        UserSummaryProfileResponseDto response = userProfileQueryService.getSummaryByUserId(1L);

        // then
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.publicId()).isEqualTo("TCAT-00000001");
    }

    @Test
    @DisplayName("publicId 기반 사용자 요약 프로필 조회 테스트")
    void getSummaryByPublicId() {
        // given
        User user = createUser(1L, "user@example.com", "user", "TCAT-00000001");

        when(userRepository.findByPublicId("TCAT-00000001")).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserAndDeletedFalse(user)).thenReturn(Optional.empty());

        // when
        UserSummaryProfileResponseDto response = userProfileQueryService.getSummaryByPublicId("TCAT-00000001");

        // then
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.publicId()).isEqualTo("TCAT-00000001");
    }

    @Test
    @DisplayName("존재하지 않는 publicId는 실패한다")
    void failWhenPublicIdDoesNotExist() {
        // given
        when(userRepository.findByPublicId("UNKNOWN")).thenReturn(Optional.empty());

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> userProfileQueryService.getSummaryByPublicId("UNKNOWN"))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("PUBLIC_ID_NOT_FOUND")
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
