package jp.co.translacat.domain.user.deletion;

import jp.co.translacat.domain.user.auth.AuthExceptionAdvice;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 컨트롤러의 bounded/strict JSON 및 no-store 계약 검증. 실제 filter 인증 검증과 구분한다. */
class AccountDeletionControllerTest {
    private MockMvc mvc;
    private AccountDeletionService deletion;
    private AccountDeletionReauthentication reauth;
    private final String sid = UUID.randomUUID().toString();

    @BeforeEach
    void prepare() {
        // 준비: 서비스/토큰은 합성 mock이며 운영 설정과 DB를 읽지 않는다.
        deletion = mock(AccountDeletionService.class);
        reauth = mock(AccountDeletionReauthentication.class);
        JWTService jwt = mock(JWTService.class);
        when(jwt.getSessionId("synthetic")).thenReturn(sid);
        mvc = MockMvcBuilders.standaloneSetup(new AccountDeletionController(deletion, reauth, jwt))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new AuthExceptionAdvice()).build();
        User user = User.createLocalUser("synthetic@example.com", "hash", "Synthetic", Role.USER, "TCAT-SYNTHETIC");
        user.setId(42L);
        var principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void successfulProofResponseIsNoStoreAndBoundToAuthenticatedActorAndSid() throws Exception {
        // 준비
        when(reauth.password(42, sid, "synthetic-password")).thenReturn(new AccountDeletionReauthentication.ProofResponse("synthetic-proof", 300));

        // 실행
        mvc.perform(post("/api/v1/users/me/deletion-reauthentication/password")
                        .header("Authorization", "Bearer synthetic").contentType("application/json").content("{\"password\":\"synthetic-password\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));

        // 검증
        verify(reauth).password(42, sid, "synthetic-password");
    }

    @Test
    void unknownActorFieldAndDuplicateKeysAreRejectedWithoutServiceCall() throws Exception {
        // 준비 및 실행
        for (String body : java.util.List.of("{\"password\":\"secret\",\"actorId\":7}", "{\"password\":\"first\",\"password\":\"second\"}")) {
            mvc.perform(post("/api/v1/users/me/deletion-reauthentication/password")
                            .header("Authorization", "Bearer synthetic").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"));
        }

        // 검증
        verifyNoInteractions(reauth);
    }

    @Test
    void oversizedBodyCannotReachReauthenticationService() throws Exception {
        // 준비
        String body = "{\"password\":\"" + "x".repeat(25000) + "\"}";

        // 실행
        mvc.perform(post("/api/v1/users/me/deletion-reauthentication/password")
                        .header("Authorization", "Bearer synthetic").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());

        // 검증
        verifyNoInteractions(reauth);
    }

    @Test
    void receiptStatusDoesNotRequireCurrentLoginAndAlwaysReturnsNoStore() throws Exception {
        // 준비
        SecurityContextHolder.clearContext();
        String id = UUID.randomUUID().toString();
        String receipt = DeletionTokens.issue();
        when(deletion.status(any(), any())).thenReturn(new AccountDeletionService.Status(id, "PENDING", Instant.now(), null, null));

        // 실행
        mvc.perform(post("/api/v1/account-deletion/status").contentType("application/json")
                        .content("{\"operationId\":\"" + id + "\",\"receiptSecret\":\"" + receipt + "\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));

        // 검증
        verify(deletion).status(eq(new AccountDeletionService.StatusRequest(id, receipt)), any());
    }
}
