package jp.co.translacat.domain.user.auth.account;

import jakarta.persistence.EntityManager;
import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.domain.user.auth.AuthAttemptLimiter;
import jp.co.translacat.domain.user.auth.email.EmailVerificationPurpose;
import jp.co.translacat.domain.user.auth.email.EmailVerificationService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.enums.SocialType;
import jp.co.translacat.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountAuthServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-11T00:00:00Z");
    private final UserRepository users = mock(UserRepository.class);
    private final GoogleAuthIntentRepository intents = mock(GoogleAuthIntentRepository.class);
    private final ReauthenticationGrantRepository grants = mock(ReauthenticationGrantRepository.class);
    private final GoogleIdentityVerifier google = mock(GoogleIdentityVerifier.class);
    private final EmailVerificationService email = mock(EmailVerificationService.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(4);
    private final AccountAuthService service = new AccountAuthService(users, intents, grants, google, email,
            passwords, mock(PlatformTransactionManager.class), mock(AuthAttemptLimiter.class), Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void preparePersistenceBoundary() {
        // 준비: 실제 DB 재조회/잠금 동작은 별도 통합 테스트에서 검증한다.
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));
    }

    @Test
    void linkStartRequiresExistingPasswordBeforeIssuingIntent() {
        // 준비
        User user = localUser();
        when(users.findLockedById(1L)).thenReturn(Optional.of(user));

        // 실행 및 검증
        assertError(() -> service.startGoogle(1L, GoogleAuthPurpose.LINK, "wrong-password"), "CURRENT_PASSWORD_INVALID");
        verifyNoInteractions(intents, google);
    }

    @Test
    void startStoresHashesAndBindsIntentToUserAndPurpose() {
        // 준비
        when(users.findLockedById(1L)).thenReturn(Optional.of(localUser()));

        // 실행
        var result = service.startGoogle(1L, GoogleAuthPurpose.LINK, "existing-password");

        // 검증
        var captor = ArgumentCaptor.forClass(GoogleAuthIntent.class);
        verify(intents).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getTokenHash()).isEqualTo(AccountProofTokens.hash(result.intentToken())).isNotEqualTo(result.intentToken());
        assertThat(saved.getNonceHash()).isEqualTo(AccountProofTokens.hash(result.nonce())).isNotEqualTo(result.nonce());
        assertThat(saved.getPurpose()).isEqualTo(GoogleAuthPurpose.LINK);
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    void googleLinkKeepsOriginalAccountAndCannotReplayIntent() {
        // 준비
        User user = localUser();
        String originalPassword = user.getPassword();
        GoogleAuthIntent intent = prepareIntent(user, GoogleAuthPurpose.LINK, NOW.plusSeconds(300));
        when(google.verify("signed-token")).thenReturn(identity("google-sub", "user@example.com", "nonce"));

        // 실행
        var result = service.completeGoogle(1L, "intent", "signed-token");

        // 검증: 로그인 수단만 추가하고 기존 식별자와 비밀번호는 그대로 유지한다.
        assertThat(result).containsEntry("linked", true);
        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getPublicId()).isEqualTo("TCAT-USER0001");
        assertThat(user.getPassword()).isEqualTo(originalPassword);
        assertThat(user.isGoogleLinked()).isTrue();
        assertThat(intent.getConsumedAt()).isEqualTo(NOW);
        assertError(() -> service.completeGoogle(1L, "intent", "signed-token"), "AUTH_PROOF_INVALID_OR_EXPIRED");
        verify(google, times(1)).verify(any());
    }

    @Test
    void intentCannotBeUsedByDifferentLoggedInUser() {
        // 준비
        prepareIntent(localUser(), GoogleAuthPurpose.LINK, NOW.plusSeconds(300));

        // 실행 및 검증
        assertError(() -> service.completeGoogle(2L, "intent", "signed-token"), "AUTH_PROOF_INVALID_OR_EXPIRED");
        verifyNoInteractions(google);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void expiryBoundaryIsRejectedBeforeGoogleVerification() {
        // 준비
        prepareIntent(localUser(), GoogleAuthPurpose.LINK, NOW);

        // 실행 및 검증
        assertError(() -> service.completeGoogle(1L, "intent", "signed-token"), "AUTH_PROOF_INVALID_OR_EXPIRED");
        verifyNoInteractions(google);
    }

    @Test
    void nonceMismatchCannotLinkEvenWithSameVerifiedEmail() {
        // 준비
        User user = localUser();
        GoogleAuthIntent intent = prepareIntent(user, GoogleAuthPurpose.LINK, NOW.plusSeconds(300));
        when(google.verify("signed-token")).thenReturn(identity("google-sub", "user@example.com", "different-nonce"));

        // 실행 및 검증
        assertError(() -> service.completeGoogle(1L, "intent", "signed-token"), "GOOGLE_NONCE_MISMATCH");
        assertThat(user.isGoogleLinked()).isFalse();
        assertThat(intent.getConsumedAt()).isNull();
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void googleEmailMismatchCannotLink() {
        // 준비
        prepareIntent(localUser(), GoogleAuthPurpose.LINK, NOW.plusSeconds(300));
        when(google.verify("signed-token")).thenReturn(identity("google-sub", "other@example.com", "nonce"));

        // 실행 및 검증
        assertError(() -> service.completeGoogle(1L, "intent", "signed-token"), "GOOGLE_EMAIL_MISMATCH");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void googleSubjectAlreadyOwnedByAnotherUserCannotMove() {
        // 준비
        prepareIntent(localUser(), GoogleAuthPurpose.LINK, NOW.plusSeconds(300));
        when(google.verify("signed-token")).thenReturn(identity("google-sub", "user@example.com", "nonce"));
        User other = googleUser();
        other.setId(2L);
        when(users.findBySocialIdAndSocialType("google-sub", SocialType.GOOGLE)).thenReturn(Optional.of(other));

        // 실행 및 검증
        assertError(() -> service.completeGoogle(1L, "intent", "signed-token"), "GOOGLE_ALREADY_LINKED");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void reauthenticationRejectsDifferentSubjectEvenWhenEmailMatches() {
        // 준비
        prepareIntent(googleUser(), GoogleAuthPurpose.REAUTHENTICATE, NOW.plusSeconds(300));
        when(google.verify("signed-token")).thenReturn(identity("other-sub", "user@example.com", "nonce"));

        // 실행 및 검증
        assertError(() -> service.completeGoogle(1L, "intent", "signed-token"), "GOOGLE_ACCOUNT_MISMATCH");
        verifyNoInteractions(grants);
    }

    @Test
    void reauthenticationAcceptsLinkedSubjectAfterProviderEmailChanges() {
        // 준비
        prepareIntent(googleUser(), GoogleAuthPurpose.REAUTHENTICATE, NOW.plusSeconds(300));
        when(google.verify("signed-token")).thenReturn(identity("google-sub", "changed@example.com", "nonce"));

        // 실행
        var result = service.completeGoogle(1L, "intent", "signed-token");

        // 검증: 기존 연결의 subject를 확인한 경우에만 비밀번호 추가용 증명을 만든다.
        var captor = ArgumentCaptor.forClass(ReauthenticationGrant.class);
        verify(grants).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo(AccountProofTokens.hash((String) result.get("reauthToken")));
        assertThat(captor.getValue().getPurpose()).isEqualTo("ADD_PASSWORD");
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
    }

    @Test
    void passwordAdditionConsumesBothProofsAndPreservesGoogleLink() {
        // 준비
        User user = googleUser();
        when(users.findLockedById(1L)).thenReturn(Optional.of(user));
        ReauthenticationGrant grant = prepareGrant(1L, NOW.plusSeconds(300));

        // 실행
        service.addPassword(1L, "new-password-2026", "email-proof", "reauth");

        // 검증
        verify(email).consume("email-proof", "user@example.com", EmailVerificationPurpose.ADD_PASSWORD, 1L);
        assertThat(passwords.matches("new-password-2026", user.getPassword())).isTrue();
        assertThat(user.isGoogleLinked()).isTrue();
        assertThat(user.getSocialId()).isEqualTo("google-sub");
        assertThat(grant.getConsumedAt()).isEqualTo(NOW);
        assertThat(user.getEmailVerifiedAt()).isEqualTo(NOW);
    }

    @Test
    void passwordAdditionRejectsAnotherUsersReauthenticationProof() {
        // 준비
        when(users.findLockedById(1L)).thenReturn(Optional.of(googleUser()));
        prepareGrant(2L, NOW.plusSeconds(300));

        // 실행 및 검증
        assertError(() -> service.addPassword(1L, "new-password-2026", "email-proof", "reauth"), "AUTH_PROOF_INVALID_OR_EXPIRED");
        verifyNoInteractions(email);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void passwordAdditionRejectsExpiredAndConsumedProofs() {
        // 준비
        when(users.findLockedById(1L)).thenReturn(Optional.of(googleUser()));
        prepareGrant(1L, NOW);

        // 실행 및 검증: 만료시각과 같은 시점부터 거절한다.
        assertError(() -> service.addPassword(1L, "new-password-2026", "email-proof", "reauth"), "AUTH_PROOF_INVALID_OR_EXPIRED");

        // 준비 및 검증: 유효기간 안이어도 이미 사용한 증명은 거절한다.
        ReauthenticationGrant consumed = prepareGrant(1L, NOW.plusSeconds(300));
        consumed.consume(NOW.minusSeconds(1));
        assertError(() -> service.addPassword(1L, "new-password-2026", "email-proof", "reauth"), "AUTH_PROOF_INVALID_OR_EXPIRED");
        verifyNoInteractions(email);
    }

    @Test
    void passwordAdditionRejectsProofForDifferentPurpose() {
        // 준비: 다른 작업용 증명이 같은 사용자에게 발급된 경우를 재현한다.
        when(users.findLockedById(1L)).thenReturn(Optional.of(googleUser()));
        ReauthenticationGrant grant = prepareGrant(1L, NOW.plusSeconds(300));
        ReflectionTestUtils.setField(grant, "purpose", "OTHER_ACTION");

        // 실행 및 검증
        assertError(() -> service.addPassword(1L, "new-password-2026", "email-proof", "reauth"), "AUTH_PROOF_INVALID_OR_EXPIRED");
        verifyNoInteractions(email);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void passwordAdditionNeverOverwritesExistingPassword() {
        // 준비
        User user = googleUser();
        user.setPassword("existing-encoded-password");
        when(users.findLockedById(1L)).thenReturn(Optional.of(user));

        // 실행 및 검증
        assertError(() -> service.addPassword(1L, "new-password-2026", "email-proof", "reauth"), "PASSWORD_ALREADY_SET");
        assertThat(user.getPassword()).isEqualTo("existing-encoded-password");
        verifyNoInteractions(email, grants);
    }

    private GoogleAuthIntent prepareIntent(User user, GoogleAuthPurpose purpose, Instant expiry) {
        GoogleAuthIntent intent = GoogleAuthIntent.create("intent", 1L, purpose, "nonce", expiry);
        String hash = AccountProofTokens.hash("intent");
        when(intents.findStateByHash(hash)).thenAnswer(call -> Optional.of(
                new GoogleAuthIntentSnapshot(intent.getUserId(), intent.getExpiresAt(), intent.getConsumedAt())));
        when(intents.findLockedByHash(hash)).thenReturn(Optional.of(intent));
        when(users.findLockedById(1L)).thenReturn(Optional.of(user));
        return intent;
    }

    private ReauthenticationGrant prepareGrant(Long userId, Instant expiry) {
        ReauthenticationGrant grant = ReauthenticationGrant.create("reauth", userId, expiry);
        when(grants.findLockedByHash(AccountProofTokens.hash("reauth"))).thenReturn(Optional.of(grant));
        return grant;
    }

    private GoogleIdentityVerifier.Identity identity(String subject, String email, String nonce) {
        return new GoogleIdentityVerifier.Identity(subject, email, "Google name", nonce);
    }

    private User localUser() {
        User user = User.createLocalUser("user@example.com", passwords.encode("existing-password"), "Nickname", Role.USER, "TCAT-USER0001");
        user.setId(1L);
        return user;
    }

    private User googleUser() {
        User user = User.createSocialUser("user@example.com", "Nickname", SocialType.GOOGLE, "google-sub", Role.USER, "TCAT-USER0001");
        user.setId(1L);
        return user;
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(AuthException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo(code));
    }
}
