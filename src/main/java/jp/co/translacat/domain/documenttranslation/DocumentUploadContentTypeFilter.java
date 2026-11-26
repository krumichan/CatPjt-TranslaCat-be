package jp.co.translacat.domain.documenttranslation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;

/** 문서 접수에만 적용하며 서블릿이 멀티파트 원본을 임시 저장하기 전에 거부한다. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "translacat.document-translation.enabled", havingValue = "true")
final class DocumentUploadContentTypeFilter extends OncePerRequestFilter {

    /** 다른 기능의 서블릿 업로드 계약은 그대로 통과시킨다. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return !DocumentTranslationController.BASE.equals(path)
                && !path.startsWith(DocumentTranslationController.BASE + "/");
    }

    /** 바이트 통과 여부는 Content-Type만 확인하며 요청 본문에 접근하지 않는다. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        String contentType = request.getContentType();
        boolean submit = "POST".equals(request.getMethod()) && (DocumentTranslationController.BASE.equals(path)
                || (DocumentTranslationController.BASE + "/").equals(path));
        if (submit ? isRawDocument(contentType) : !isMultipart(contentType)) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"code\":\"UNSUPPORTED_MEDIA_TYPE\",\"message\":\"UNSUPPORTED_MEDIA_TYPE\"}");
    }

    // 올바른 MIME에 선택적인 매개변수만 허용한다.
    private static boolean isRawDocument(String contentType) {
        if (contentType == null) {
            return false;
        }
        try {
            return MediaType.APPLICATION_OCTET_STREAM.includes(MediaType.parseMediaType(contentType));
        } catch (InvalidMediaTypeException invalid) {
            return false;
        }
    }

    // 문서 상태·취소 등 본문이 필요 없는 요청에서도 서블릿 멀티파트 해석을 방지한다.
    private static boolean isMultipart(String contentType) {
        return contentType != null && contentType.stripLeading().regionMatches(true, 0, "multipart/", 0, 10);
    }
}
