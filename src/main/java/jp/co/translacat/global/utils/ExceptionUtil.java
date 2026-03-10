package jp.co.translacat.global.utils;

import lombok.experimental.UtilityClass;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

/**
 * ExceptionUtility 클래스
 *
 * 예외 처리 관련 유틸리티 메서드를 제공하는 클래스입니다.
 */
@UtilityClass
public class ExceptionUtil {

    /**
     * Exception StackTrace를 문자열로 변환
     *
     * @param th Throwable 객체 (예외)
     * @return StackTrace를 문자열로 변환한 결과.
     *         th가 null이면 빈 문자열("") 반환
     */
    public String convertStackTrace(Throwable th) {
        if (Objects.isNull(th)) return "";

        // StringWriter와 PrintWriter를 사용하여 Exception StackTrace를 문자열로 캡처
        StringWriter writer = new StringWriter();
        th.printStackTrace(new PrintWriter(writer));

        return writer.toString();
    }
}
