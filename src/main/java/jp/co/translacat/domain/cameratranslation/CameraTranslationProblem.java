package jp.co.translacat.domain.cameratranslation;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** 카메라 업무 오류를 응답용 고정 코드와 연결한다. */
final class CameraTranslationProblem extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Integer retryAfter;

    /** 요청 내용 없이 업무 상태를 전달한다. */
    CameraTranslationProblem(HttpStatus status, String code) {
        this(status, code, null);
    }

    /** 제한 오류에만 다음 요청까지의 대기 초를 포함한다. */
    CameraTranslationProblem(HttpStatus status, String code, Integer retryAfter) {
        super(code);
        this.status = status;
        this.code = code;
        this.retryAfter = retryAfter;
    }

    HttpStatus status() {
        return status;
    }

    Integer retryAfter() {
        return retryAfter;
    }

    Map<String, String> body() {
        return Map.of("errorCode", code);
    }

    /** 공개 계약의 입력 오류를 만든다. */
    static CameraTranslationProblem invalid() {
        return new CameraTranslationProblem(HttpStatus.BAD_REQUEST, "INVALID_INPUT");
    }

    /** 소유자가 읽을 수 있는 작업이 없음을 나타낸다. */
    static CameraTranslationProblem notFound() {
        return new CameraTranslationProblem(HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    /** 일시 장애를 본문이나 원인 메시지 없이 보고한다. */
    static CameraTranslationProblem unavailable() {
        return new CameraTranslationProblem(HttpStatus.SERVICE_UNAVAILABLE, "TEMPORARILY_UNAVAILABLE");
    }
}
