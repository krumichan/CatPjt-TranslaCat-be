package jp.co.translacat.domain.novelgateway;

import jp.co.translacat.infrastructure.novel.client.NovelGatewayTrace;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.LinkedHashSet;
import java.util.Locale;

@RestControllerAdvice(assignableTypes = {NovelReaderController.class, NovelCatalogController.class, NovelReaderExceptionAdvice.class})
public class NovelReaderTraceAdvice implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter type, Class<? extends HttpMessageConverter<?>> converter) {
        return type.getContainingClass() == NovelReaderController.class
                || type.getContainingClass() == NovelCatalogController.class
                || type.getContainingClass() == NovelReaderExceptionAdvice.class;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter type, MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request,
            ServerHttpResponse response) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return body;
        }
        var trace = NovelGatewayTrace.forRequest(servletRequest.getServletRequest());
        response.getHeaders().set(NovelGatewayTrace.HEADER, trace.id());
        var exposed = new LinkedHashSet<>(response.getHeaders().getAccessControlExposeHeaders());
        exposed.add(NovelGatewayTrace.HEADER);

        // 응답이 commit되기 전에 기록한다. 전체 BE 값은 body 직렬화/네트워크 송신 전까지의 시간이다.
        put(response, exposed, "X-Novel-Be-Duration-Ms", trace.elapsedMs());
        put(response, exposed, "X-Novel-Be-Auth-Dispatch-Ms", trace.authDispatchMs());
        put(response, exposed, "X-Novel-Upstream-Headers-Ms", trace.upstreamHeadersMs());
        put(response, exposed, "X-Novel-Upstream-Duration-Ms", trace.upstreamMs());
        put(response, exposed, "X-Novel-Be-Validation-Ms", trace.validationMs());
        if (response instanceof ServletServerHttpResponse servletResponse) {
            // Spring의 addHeader 단계와 중복되지 않게 기존 CORS 값은 Servlet 응답에서 한 번만 교체한다.
            response.getHeaders().remove("Access-Control-Expose-Headers");
            servletResponse.getServletResponse().setHeader("Access-Control-Expose-Headers", String.join(", ", exposed));
        } else {
            response.getHeaders().setAccessControlExposeHeaders(exposed.stream().toList());
        }
        return body;
    }

    private void put(ServerHttpResponse response, LinkedHashSet<String> exposed, String name, Double value) {
        if (value != null) {
            response.getHeaders().set(name, String.format(Locale.ROOT, "%.3f", value));
            exposed.add(name);
        }
    }
}
