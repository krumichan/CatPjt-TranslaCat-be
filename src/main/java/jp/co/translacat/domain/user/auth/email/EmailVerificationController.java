package jp.co.translacat.domain.user.auth.email;

import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/email")
public class EmailVerificationController {
    private final EmailVerificationService service;

    @PostMapping("/challenges")
    public ResponseDto<EmailChallengeResponse> send(@RequestBody ChallengeRequest request) {
        return ResponseUtil.ok(service.sendRegistration(request.email()));
    }

    @PostMapping("/verify")
    public ResponseDto<EmailVerificationResponse> verify(@RequestBody VerifyRequest request) {
        return ResponseUtil.ok(service.verify(request.challengeId(), request.code()));
    }

    public record ChallengeRequest(String email) {
    }

    public record VerifyRequest(String challengeId, String code) {
    }
}
