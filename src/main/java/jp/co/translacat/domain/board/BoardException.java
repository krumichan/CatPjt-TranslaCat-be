package jp.co.translacat.domain.board;

import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.global.dto.ErrorDto;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

final class BoardException extends RuntimeException {

    final HttpStatus status;

    BoardException(HttpStatus status, String code) {
        super(code);
        this.status = status;
    }

    static BoardException missing() {
        return new BoardException(HttpStatus.NOT_FOUND, "BOARD_NOT_FOUND");
    }

    static BoardException locked() {
        return new BoardException(HttpStatus.FORBIDDEN, "BOARD_LOCKED");
    }

    static BoardException invalid() {
        return new BoardException(HttpStatus.BAD_REQUEST, "BOARD_INVALID_INPUT");
    }

    static BoardException storage() {
        return new BoardException(HttpStatus.SERVICE_UNAVAILABLE, "BOARD_STORAGE_UNAVAILABLE");
    }

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @RestControllerAdvice(assignableTypes = BoardController.class)
    static class Advice {

        @ExceptionHandler({
                MethodArgumentNotValidException.class,
                HttpMessageNotReadableException.class,
                MethodArgumentTypeMismatchException.class
        })
        ResponseEntity<ResponseDto<ErrorDto>> invalidRequest() {
            return handle(BoardException.invalid());
        }

        @ExceptionHandler(AuthException.class)
        ResponseEntity<ResponseDto<ErrorDto>> throttled(AuthException error) {
            // 비밀번호 대입 제한을 전역 500 처리기에 넘기지 않고 재시도 시간을 반환한다.
            var response = ResponseEntity.status(error.getStatus()).cacheControl(CacheControl.noStore());
            if (error.getRetryAfterSeconds() != null) {
                response.header(HttpHeaders.RETRY_AFTER, error.getRetryAfterSeconds().toString());
            }

            return response.body(ResponseUtil.<ErrorDto>error(
                    error.getStatus().value(), error.getErrorCode(), error.getErrorCode(), null, false));
        }

        @ExceptionHandler(DataAccessException.class)
        ResponseEntity<ResponseDto<ErrorDto>> persistenceFailure() {
            return handle(new BoardException(HttpStatus.SERVICE_UNAVAILABLE, "BOARD_UNAVAILABLE"));
        }

        @ExceptionHandler(BoardException.class)
        ResponseEntity<ResponseDto<ErrorDto>> handle(BoardException error) {
            return ResponseEntity.status(error.status)
                    .cacheControl(CacheControl.noStore())
                    .body(ResponseUtil.<ErrorDto>error(
                            error.status.value(), error.getMessage(), error.getMessage(), null, false));
        }
    }
}
