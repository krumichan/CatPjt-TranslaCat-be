package jp.co.translacat.domain.languagelearning.webai;

import jp.co.translacat.global.dto.ErrorDto;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {WebAiLearningController.class, WebAiExternalController.class})
public class WebAiExceptionAdvice {
    @ExceptionHandler(LanguageLearningServiceException.class)
    public ResponseEntity<ResponseDto<ErrorDto>> remoteFailure(LanguageLearningServiceException failure) {
        HttpStatus status = failure.getStatus();
        String code = failure.getErrorCode();
        String message = failure.getMessage();

        // 계획 토큰 거부는 외부 401을 유지하고 서비스 JWT 오류만 내부 연결 실패로 변환한다.
        boolean internalAuthenticationFailure = status == HttpStatus.UNAUTHORIZED
                && (code == null || !code.startsWith("WEB_AI_"));
        boolean internalAuthorizationFailure = status == HttpStatus.FORBIDDEN
                && code != null && code.startsWith("INTERNAL_");
        if (internalAuthenticationFailure || internalAuthorizationFailure) {
            status = HttpStatus.BAD_GATEWAY;
            code = "LL_INTERNAL_AUTH_FAILED";
            message = "언어학습 내부 인증 설정을 확인해 주세요.";
        }
        return ResponseEntity.status(status).header("Cache-Control", "no-store")
                .body(ResponseUtil.error(status.value(), message, code, null, false));
    }
}
