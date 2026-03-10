package jp.co.translacat.global.utils;

import jp.co.translacat.global.dto.ErrorDto;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.dto.RequestContextDto;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

/**
 * ResponseUtility 클래스
 * API 응답을 생성하는 공통 유틸리티 클래스.
 */
@UtilityClass
@SuppressWarnings("unchecked")
public class ResponseUtil {

    /* ===============================
     *  SUCCESS RESPONSE
     * =============================== */

    /**
     * body만 주는 기본 성공 응답
     */
    public <T> ResponseDto<T> ok(final T body) {
        return buildResponse(HttpStatus.OK.value(), HttpStatus.OK.getReasonPhrase(), body);
    }

    /**
     * status + body
     */
    public <T> ResponseDto<T> ok(final HttpStatus status, final T body) {
        return buildResponse(status.value(), status.getReasonPhrase(), body);
    }

    /**
     * resultCode + message + body (커스텀 성공)
     */
    public <T> ResponseDto<T> ok(final int resultCode, final String message, final T body) {
        return buildResponse(resultCode, message, body);
    }

    /* ===============================
     *  ERROR RESPONSE
     * =============================== */

    /**
     * 단순 메시지 기반 에러 응답
     */
    public <T> ResponseDto<T> error(int status, String message, Throwable th, boolean debug) {
        String path = ServletRequestUtil.getRequestURI();
        String errorCode = th == null ? "" : th.getClass().getSimpleName();

        ErrorDto errorDto = buildErrorDto(errorCode, path, th, debug);
        return buildResponse(status, message, (T) errorDto);
    }

    /**
     * 에러코드 포함 에러 응답
     */
    public <T> ResponseDto<T> error(int status, String message, String errorCode, Throwable th, boolean debug) {
        String path = ServletRequestUtil.getRequestURI();

        ErrorDto errorDto = buildErrorDto(errorCode, path, th, debug);
        return buildResponse(status, message, (T) errorDto);
    }

    /* ===============================
     *  PRIVATE CORE
     * =============================== */

    private <T> ResponseDto<T> buildResponse(Integer resultCode, String message, final T body) {
        RequestContextDto contextVo = ServletRequestUtil.getServletRequestContextVo();
        String guid = contextVo != null ? contextVo.getGuid() : null;

        return ResponseDto.<T>builder()
                .resultCode(resultCode)
                .message(message)
                .body(body)
                .contextVo(contextVo)
                .guid(guid)
                .createDate(LocalDateTime.now())
                .build();
    }

    private ErrorDto buildErrorDto(String errorCode, String path, Throwable th, boolean debug) {
        String trace = debug ? ExceptionUtil.convertStackTrace(th) : "";
        return new ErrorDto(errorCode, path, trace);
    }
}
