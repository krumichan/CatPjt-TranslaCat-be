package jp.co.translacat.domain.support.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class SupportException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public SupportException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static SupportException notFound() {
        return new SupportException(HttpStatus.NOT_FOUND, "SUPPORT_NOT_FOUND", "요청한 고객센터 내용을 찾을 수 없습니다.");
    }

    public static SupportException forbidden() {
        return new SupportException(HttpStatus.FORBIDDEN, "SUPPORT_ADMIN_REQUIRED", "관리자만 사용할 수 있습니다.");
    }

    public static SupportException invalid(String message) {
        return new SupportException(HttpStatus.BAD_REQUEST, "SUPPORT_INVALID_REQUEST", message);
    }

    public static SupportException closed() {
        return new SupportException(HttpStatus.CONFLICT, "SUPPORT_INQUIRY_CLOSED", "종료한 문의는 다시 연 후 답변할 수 있습니다.");
    }
}
