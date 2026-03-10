package jp.co.translacat.global.utils;

import jakarta.servlet.http.HttpServletRequest;
import jp.co.translacat.global.dto.RequestContextDto;
import lombok.experimental.UtilityClass;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * ServletRequestUtility 클래스
 *
 * 현재 서블릿 요청(HttpServletRequest)과 관련된 정보를 가져오는 유틸리티 클래스입니다.
 * Spring Web 환경에서 RequestContextHolder를 사용하여 현재 요청을 가져옵니다.
 */
@UtilityClass
public class ServletRequestUtil {

    /**
     * 현재 HttpServletRequest 가져오기
     *
     * @return HttpServletRequest 객체, 요청이 없으면 null
     */
    public HttpServletRequest getServletRequest() {
        return Optional.of(RequestContextHolder.getRequestAttributes())
                .map(ra -> ((ServletRequestAttributes) ra).getRequest())
                .orElse(null);
    }

    /**
     * 현재 요청 URI 가져오기
     *
     * @return 요청 URI 문자열, 요청이 없으면 null
     */
    public String getRequestURI() {
        return Optional.ofNullable(ServletRequestUtil.getServletRequest())
                .map(HttpServletRequest::getRequestURI)
                .orElse(null);
    }

    /**
     * 현재 요청의 RequestContextVo 가져오기
     *
     * @return RequestContextVo 객체, 없으면 null
     *
     * @implNote
     * RequestContextVo는 요청마다 세팅되는 커스텀 컨텍스트 정보로,
     * 서블릿 request attribute "_REQUEST_CONTEXT_VO"에 저장되어 있어야 합니다.
     */
    public RequestContextDto getServletRequestContextVo() {
        return (RequestContextDto) Optional.ofNullable(getServletRequest())
                .map(req -> req.getAttribute("_REQUEST_CONTEXT_VO")).orElse(null);
    }

    /**
     * 특정 헤더 값 가져오기
     *
     * @param key 헤더 이름
     * @return 헤더 값 문자열, 없으면 null
     */
    public String getHeader(String key) {
        return Optional.ofNullable(ServletRequestUtil.getServletRequest()).map(req -> req.getHeader(key))
                .orElse(null);
    }
}
