package jp.co.translacat.domain.user.auth.mobile;

import jakarta.servlet.http.HttpServletRequest;
import jp.co.translacat.domain.user.dto.UserLoginResponseDto;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/mobile")
public class MobileAuthController {
    private final MobileAuthService mobile;
    private final MobileAuthApprovalVerifier approval;

    @PostMapping("/requests")
    public ResponseEntity<ResponseDto<MobileAuthService.RequestResponse>> create(
            @RequestBody CreateRequest request, HttpServletRequest servlet) {
        return privateResponse(mobile.create(request.codeChallenge(), request.state(), request.redirectUri(), servlet.getRemoteAddr()));
    }

    @PostMapping("/authorize")
    public ResponseEntity<ResponseDto<MobileAuthService.AuthorizeResponse>> authorize(
            @AuthenticationPrincipal UserPrincipal principal, @RequestBody AuthorizeRequest request,
            HttpServletRequest servlet) {
        // access 인증 외에 웹 BFF만 보유한 키의 승인 증명을 요구한다. 앱에는 이 키를 배포하지 않는다.
        approval.verify(request.requestId(), servlet.getHeader("Authorization"),
                servlet.getHeader(MobileAuthApprovalVerifier.TIMESTAMP_HEADER),
                servlet.getHeader(MobileAuthApprovalVerifier.SIGNATURE_HEADER));
        return privateResponse(mobile.authorize(request.requestId(), SecurityUtil.getLoginUserId(principal)));
    }

    @PostMapping("/exchange")
    public ResponseEntity<ResponseDto<UserLoginResponseDto>> exchange(
            @RequestBody ExchangeRequest request, HttpServletRequest servlet) {
        return privateResponse(mobile.exchange(request.requestId(), request.code(), request.codeVerifier(), servlet.getRemoteAddr()));
    }

    private <T> ResponseEntity<ResponseDto<T>> privateResponse(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer")
                .body(ResponseUtil.ok(value));
    }

    public record CreateRequest(String codeChallenge, String state, String redirectUri) {
    }

    public record AuthorizeRequest(String requestId) {
    }

    public record ExchangeRequest(String requestId, String code, String codeVerifier) {
    }
}
