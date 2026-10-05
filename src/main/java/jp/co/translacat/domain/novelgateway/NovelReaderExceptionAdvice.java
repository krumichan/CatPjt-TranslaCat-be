package jp.co.translacat.domain.novelgateway;

import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.infrastructure.novel.client.NovelGatewayException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {NovelReaderController.class, NovelCatalogController.class})
public class NovelReaderExceptionAdvice {
    @ExceptionHandler(NovelGatewayException.class)
    public ResponseEntity<?> failure(NovelGatewayException failure) {
        return ResponseEntity.status(failure.status()).header("Cache-Control", "no-store")
                .body(ResponseDto.builder().resultCode(failure.status()).message(failure.code())
                        .body(Map.of("code", failure.code(), "retryable", failure.retryable())).build());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> invalidBody() {
        return failure(new NovelGatewayException(400, "NOVEL_REQUEST_INVALID", false));
    }
}
