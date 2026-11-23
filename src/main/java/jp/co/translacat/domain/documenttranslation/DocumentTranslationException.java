package jp.co.translacat.domain.documenttranslation;

import org.springframework.http.HttpStatus;

/** 문서 API에서 외부에 공개하는 안정적인 실패 코드다. */
final class DocumentTranslationException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    // 내부 저장·모델 예외나 파일 내용을 사용자 응답에 전달하지 않는다.
    DocumentTranslationException(HttpStatus status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

    HttpStatus status() {
        return status;
    }

    String code() {
        return code;
    }
}
