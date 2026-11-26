package jp.co.translacat.domain.wordbook;

import com.fasterxml.jackson.annotation.JsonInclude;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 단어장 오류를 번역 본문이나 초안 없이 공통 응답에 담는다. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = WordbookController.class)
class WordbookAdvice {
    /** 파싱할 수 없는 문서는 잘못된 입력과 같은 코드를 사용한다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ResponseDto<ErrorBody>> invalid() {
        return error(WordbookProblem.invalid());
    }

    /** 저장소 오류 세부 정보 대신 일시 장애를 반환한다. */
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<ResponseDto<ErrorBody>> unavailable() {
        return error(WordbookProblem.unavailable());
    }

    /** 자신의 항목과 중복되는 편집에서만 기존 항목 ID를 포함한다. */
    @ExceptionHandler(WordbookProblem.class)
    ResponseEntity<ResponseDto<ErrorBody>> error(WordbookProblem problem) {
        var body = new ErrorBody(problem.getMessage(), problem.existingEntryId());
        return ResponseEntity.status(problem.status())
                .cacheControl(CacheControl.noStore())
                .body(ResponseUtil.ok(problem.status().value(), problem.getMessage(), body));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ErrorBody(String errorCode, java.util.UUID existingEntryId) {
    }
}
