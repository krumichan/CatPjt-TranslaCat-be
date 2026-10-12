package jp.co.translacat.domain.user.auth.account;

import jp.co.translacat.domain.user.auth.email.EmailChallengeResponse;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/account")
public class AccountAuthController {
    private final AccountAuthService accounts;

    @GetMapping
    public ResponseDto<AccountAuthService.AccountResponse> account(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseUtil.ok(accounts.account(SecurityUtil.getLoginUserId(principal)));
    }

    @PostMapping("/google/start")
    public ResponseDto<AccountAuthService.IntentResponse> start(@AuthenticationPrincipal UserPrincipal principal,
                                                                @RequestBody StartGoogleRequest request) {
        return ResponseUtil.ok(accounts.startGoogle(SecurityUtil.getLoginUserId(principal), request.purpose(), request.currentPassword()));
    }

    @PostMapping("/google/complete")
    public ResponseDto<Map<String, Object>> complete(@AuthenticationPrincipal UserPrincipal principal,
                                                    @RequestBody CompleteGoogleRequest request) {
        return ResponseUtil.ok(accounts.completeGoogle(SecurityUtil.getLoginUserId(principal), request.intentToken(), request.idToken()));
    }

    @PostMapping("/email/challenges")
    public ResponseDto<EmailChallengeResponse> sendPasswordChallenge(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseUtil.ok(accounts.sendPasswordChallenge(SecurityUtil.getLoginUserId(principal)));
    }

    @PostMapping("/password")
    public ResponseDto<Map<String, Boolean>> addPassword(@AuthenticationPrincipal UserPrincipal principal,
                                                       @RequestBody AddPasswordRequest request) {
        accounts.addPassword(SecurityUtil.getLoginUserId(principal), request.password(), request.verificationToken(), request.reauthToken());
        return ResponseUtil.ok(Map.of("success", true));
    }

    public record StartGoogleRequest(GoogleAuthPurpose purpose, String currentPassword) {
    }

    public record CompleteGoogleRequest(String intentToken, String idToken) {
    }

    public record AddPasswordRequest(String password, String verificationToken, String reauthToken) {
    }
}
