package jp.co.translacat.domain.user.auth.account;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import jp.co.translacat.domain.user.auth.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class GoogleIdentityVerifierTest {
    private final GoogleIdTokenVerifier library = mock(GoogleIdTokenVerifier.class);
    private final GoogleIdentityVerifier verifier = new GoogleIdentityVerifier("synthetic-client-id");

    @BeforeEach
    void prepare() {
        // 준비: Google 라이브러리 경계만 대체하여 외부 인증 요청 없이 실패 전파를 확인한다.
        ReflectionTestUtils.setField(verifier, "verifier", library);
    }

    @Test
    void invalidSignatureAudienceIssuerOrExpiryResultCannotBecomeIdentity() throws Exception {
        // 준비: 라이브러리가 어느 필수 검증에서든 거절하면 null을 반환한다.
        when(library.verify("rejected-token")).thenReturn(null);

        // 실행 및 검증
        assertRejected("rejected-token");
    }

    @Test
    void unverifiedEmailCannotBecomeIdentityEvenAfterLibraryVerification() throws Exception {
        // 준비
        stubVerified(false, "nonce");

        // 실행 및 검증
        assertRejected("signed-token");
    }

    @Test
    void verifiedSubjectAndNonceArePreservedForAccountBinding() throws Exception {
        // 준비
        stubVerified(true, "nonce");

        // 실행
        var identity = verifier.verify("signed-token");

        // 검증: 로그인 식별자와 연결 요청 nonce를 서로 다른 값으로 유지한다.
        assertThat(identity.subject()).isEqualTo("google-subject");
        assertThat(identity.email()).isEqualTo("verified@example.com");
        assertThat(identity.nonce()).isEqualTo("nonce");
    }

    @Test
    void networkFailureDoesNotExposeTokenOrUpstreamDetails() throws Exception {
        // 준비
        when(library.verify("private-token")).thenThrow(new IOException("sensitive upstream detail"));

        // 실행 및 검증
        assertThatThrownBy(() -> verifier.verify("private-token")).isInstanceOfSatisfying(AuthException.class, failure -> {
            assertThat(failure.getErrorCode()).isEqualTo("GOOGLE_AUTH_INVALID");
            assertThat(failure.getMessage()).doesNotContain("private-token", "sensitive upstream detail");
            assertThat(failure.getCause()).isNull();
        });
    }

    @Test
    void missingAndOversizedTokensNeverReachGoogleLibrary() {
        // 실행 및 검증
        assertRejected(null);
        assertRejected(" ");
        assertRejected("x".repeat(16385));
        verifyNoInteractions(library);
    }

    private void stubVerified(boolean emailVerified, String nonce) throws Exception {
        var token = mock(GoogleIdToken.class);
        var payload = new GoogleIdToken.Payload();
        payload.setSubject("google-subject");
        payload.setEmail("verified@example.com");
        payload.setEmailVerified(emailVerified);
        payload.set("name", "Synthetic user");
        payload.set("nonce", nonce);
        when(token.getPayload()).thenReturn(payload);
        when(library.verify("signed-token")).thenReturn(token);
    }

    private void assertRejected(String token) {
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOfSatisfying(AuthException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo("GOOGLE_AUTH_INVALID"));
    }
}
