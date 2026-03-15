package jp.co.translacat.global.logging;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

@Slf4j
@Component
public class ApiLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain chain)
    throws IOException, ServletException {

        // Multipart의 경우, request를 읽어들이면 Controller 단에서 정보가 얻어지지 않음.
        // 따라서, 바로 반환.
        String contentType = request.getContentType();
        if (contentType != null && contentType.contains(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            chain.doFilter(request, response);
            return;
        }

        // Caching 가능한 Wrapper 클래스로 원본 Request Body 복사.
        CustomCachingRequestWrapper requestWrapper = new CustomCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        // Controller Logic 실행 전, Request Log 출력
        this.logRequest(requestWrapper);

        try {
            // Controller에 wrapper 전달.
            // Controller 응답 데이터를 responseWrapper에 기록.
            chain.doFilter(requestWrapper, responseWrapper);
        } finally {
            // Controller Logic 실행 후, Response Log 출력
            this.logResponse(responseWrapper);

            // Response Wrapper가 가로채고 있던 응답 데이터를 실제 HttpServletResponse의 출력 스트림에 기록.
            // 이에 의해, 프론트앤드가 물리적으로 데이터를 전송받을 수 있음.
            responseWrapper.copyBodyToResponse();
        }
    }

    private void logRequest(CustomCachingRequestWrapper request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        String username = SecurityUtil.getSafeUsername();
        String body;

        // 본문 내용을 Byte[]로 읽어들임.
        // ContentCachingRequestWrapper는 반드시 누군가 Body를 한 번 읽어들여야,
        // 내부적으로 Caching 기록이 가능하다.
        // 이는 보통 Controller 측에서 수행을 하나,
        // 우리는 Controller 실행 전에 Request를 로그로 출력하기 위해,
        // CustomCachingRequestWrapper를 만들어 내부적으로 미리 바디 값을 읽어들인다.
        body = new String(request.getContentAsByteArray());

        log.info("[REQUEST] [{}] {} {} | Body: {}", username, method, uri, body);
    }

    private void logResponse(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        String body = new String(content);

        // 로그 길이 제한. ( 무분별한 텍스트 대량 출력을 방지하기 위함 )
        String trimmedBody = (body.length() > 200) ? body.substring(0, 200) + "... (TRUNCATED)" : body;

        // 원본 길이 기록.
        log.info("[RESPONSE] Status: {}, Length: {} bytes, Body: {}",
                response.getStatus(), content.length, trimmedBody);
    }
}
