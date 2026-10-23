package jp.co.translacat.domain.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.user.dto.UserCreateRequestDto;
import jp.co.translacat.domain.user.repository.RefreshTokenRepository;
import jp.co.translacat.domain.user.repository.UserAllowedRepository;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.domain.user.auth.AuthAttemptLimiter;
import jp.co.translacat.domain.user.auth.email.EmailVerificationService;
import jp.co.translacat.domain.user.auth.email.EmailVerificationPurpose;
import jp.co.translacat.domain.user.profile.repository.UserProfileRepository;
import jp.co.translacat.domain.user.profile.entity.UserProfile;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.enums.SocialType;
import jp.co.translacat.global.security.JWTService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class UserRegistrationPasswordTest {
    @Mock
    UserRepository userRepository;
    @Mock
    RefreshTokenRepository refreshTokenRepository;
    @Mock
    UserAllowedRepository userAllowedRepository;
    @Mock
    JWTService jwtService;
    @Mock
    AuthenticationManager authenticationManager;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    EmailVerificationService emailVerification;
    @Mock
    UserProfileRepository userProfiles;
    @Mock
    AuthAttemptLimiter attempts;
    @Mock
    jp.co.translacat.domain.user.deletion.AccountDeletionIdentityGate deletionIdentities;
    @InjectMocks
    UserService userService;

    @Test
    void pendingDeletionReservationStopsLocalRegistrationBeforeConsumingProof() throws Exception {
        // 준비
        doThrow(new AuthException(HttpStatus.CONFLICT, "ACCOUNT_DELETION_PENDING", "pending"))
                .when(deletionIdentities).requireNewAccountAllowed("receipt@example.com");

        // 실행
        assertThatThrownBy(() -> userService.register(request("receipt@example.com", "proof"))).isInstanceOf(AuthException.class);

        // 검증
        verifyNoInteractions(emailVerification, userProfiles);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void pendingDeletionReservationAlsoStopsAutomaticSocialRegistration() {
        // 준비
        when(userRepository.findBySocialIdAndSocialType("new-subject", SocialType.GOOGLE)).thenReturn(Optional.empty());
        doThrow(new AuthException(HttpStatus.CONFLICT, "ACCOUNT_DELETION_PENDING", "pending"))
                .when(deletionIdentities).requireNewAccountAllowed("receipt@example.com");

        // 실행
        assertThatThrownBy(() -> userService.getOrRegisterSocialUser("receipt@example.com", "New", "new-subject", SocialType.GOOGLE)).isInstanceOf(AuthException.class);

        // 검증
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void localRegistrationStoresOnlyTheEncodedPassword() throws Exception {
        // 준비: 검증된 이메일과 비공개 생년월일을 가진 가입 요청이다.
        var request = request("receipt@example.com", "proof");
        when(userRepository.findByEmailIgnoreCase(request.getEmail())).thenReturn(Optional.empty());
        when(userRepository.existsByPublicId(any())).thenReturn(false);
        when(passwordEncoder.encode("plain-secret")).thenReturn("encoded-secret");
        when(userRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // 실행
        var saved = userService.register(request);

        // 검증: 인증 증명을 소비하고 같은 가입 흐름에서 프로필도 생성한다.
        assertThat(saved.getPassword()).isEqualTo("encoded-secret");
        verify(passwordEncoder).encode("plain-secret");
        verify(emailVerification).consume("proof", "receipt@example.com", EmailVerificationPurpose.REGISTER, null);
        assertThat(saved.getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 2));
        assertThat(saved.getEmailVerifiedAt()).isNotNull();
        var profile = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfiles).saveAndFlush(profile.capture());
        assertThat(profile.getValue().getNickname()).isEqualTo("receipt");
    }

    @Test
    void unverifiedRegistrationCannotStoreUserOrProfile() throws Exception {
        // 준비
        var request = request("receipt@example.com", "invalid");
        doThrow(new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_PROOF", "invalid"))
                .when(emailVerification).consume("invalid", request.getEmail(), EmailVerificationPurpose.REGISTER, null);

        // 실행 및 검증
        assertThatThrownBy(() -> userService.register(request)).isInstanceOf(AuthException.class);
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(userProfiles, passwordEncoder);
    }

    @Test
    void existingEmailCannotBeRegisteredAgainAfterVerification() throws Exception {
        // 준비
        var request = request("receipt@example.com", "proof");
        when(userRepository.findByEmailIgnoreCase(request.getEmail())).thenReturn(Optional.of(localUser()));

        // 실행 및 검증
        assertThatThrownBy(() -> userService.register(request)).isInstanceOfSatisfying(AuthException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo("ACCOUNT_ALREADY_EXISTS"));
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(userProfiles, passwordEncoder);
    }

    @Test
    void sameEmailGoogleLoginRequiresExplicitLinkInsteadOfCreatingSecondUser() {
        // 준비
        when(userRepository.findBySocialIdAndSocialType("google-sub", SocialType.GOOGLE)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("receipt@example.com")).thenReturn(Optional.of(localUser()));

        // 실행 및 검증
        assertThatThrownBy(() -> userService.getOrRegisterSocialUser("receipt@example.com", "Google", "google-sub", SocialType.GOOGLE))
                .isInstanceOfSatisfying(AuthException.class, error -> assertThat(error.getErrorCode()).isEqualTo("ACCOUNT_LINK_REQUIRED"));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void linkedSubjectKeepsExistingUserWhenGoogleEmailChanges() {
        // 준비
        User existing = localUser();
        existing.linkGoogle("google-sub");
        when(userRepository.findBySocialIdAndSocialType("google-sub", SocialType.GOOGLE)).thenReturn(Optional.of(existing));

        // 실행
        var actual = userService.getOrRegisterSocialUser("changed@example.com", "Changed", "google-sub", SocialType.GOOGLE);

        // 검증: 원래 프로필과 로그인 이메일을 다른 Google 속성으로 덮어쓰지 않는다.
        assertThat(actual).isSameAs(existing);
        assertThat(actual.getEmail()).isEqualTo("receipt@example.com");
        assertThat(actual.getUsername()).isEqualTo("receipt");
        verify(userRepository, never()).findByEmailIgnoreCase(any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    private UserCreateRequestDto request(String email, String proof) throws Exception {
        return new ObjectMapper().findAndRegisterModules().readValue(
                "{\"email\":\"" + email + "\",\"password\":\"plain-secret\",\"nickname\":\"receipt\","
                        + "\"birthDate\":\"2000-01-02\",\"verificationToken\":\"" + proof + "\"}",
                UserCreateRequestDto.class);
    }

    private User localUser() {
        return User.createLocalUser("receipt@example.com", "encoded", "receipt", Role.USER, "TCAT-AUDIT001");
    }
}
