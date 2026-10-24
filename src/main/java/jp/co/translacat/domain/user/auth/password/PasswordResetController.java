package jp.co.translacat.domain.user.auth.password;

import jp.co.translacat.domain.user.auth.email.EmailChallengeResponse;
import jp.co.translacat.domain.user.auth.email.EmailVerificationResponse;
import jp.co.translacat.domain.user.auth.email.EmailVerificationService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/password-reset")
public class PasswordResetController {
    private final EmailVerificationService verification;
    private final PasswordResetService passwords;

    @PostMapping("/challenges")
    public ResponseDto<EmailChallengeResponse> send(@RequestBody ChallengeRequest request) {
        return ResponseUtil.ok(verification.sendPasswordReset(request.email()));
    }

    @PostMapping("/verify")
    public ResponseDto<EmailVerificationResponse> verify(@RequestBody VerifyRequest request) {
        return ResponseUtil.ok(verification.verifyPasswordReset(request.challengeId(), request.code()));
    }

    @PostMapping("/complete")
    public ResponseDto<Completed> complete(@RequestBody CompleteRequest request) {
        passwords.complete(request.email(), request.verificationToken(), request.password());
        return ResponseUtil.ok(new Completed(true));
    }

    public record ChallengeRequest(String email) {}
    public record VerifyRequest(String challengeId, String code) {}
    public record CompleteRequest(String email, String verificationToken, String password) {}
    public record Completed(boolean success) {}
}
