package jp.co.translacat.global.exception;

import jp.co.translacat.global.dto.ErrorDto;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "jp.co.translacat.domain.languagelearning")
public class LanguageLearningRequestExceptionAdvice {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseDto<ErrorDto>> malformedLearningRequest() {
        // Jackson 예외에는 제출 원문이 포함될 수 있으므로 고정 문구와 코드만 반환한다.
        ResponseDto<ErrorDto> response = ResponseUtil.error(
                400,
                "언어학습 요청 형식을 확인해 주세요.",
                "LANGUAGE_LEARNING_REQUEST_INVALID",
                null,
                false
        );
        return ResponseEntity.badRequest().body(response);
    }
}
