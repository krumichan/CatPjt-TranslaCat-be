package jp.co.translacat.infrastructure.novel.client;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.UUID;
import java.util.regex.Pattern;

/** 본문이나 인증값 없이 현재 HTTP 요청의 상관 ID와 단조 시계 구간만 보관한다. */
public final class NovelGatewayTrace {
    public static final String HEADER = "X-Novel-Trace-Id";
    private static final String ATTRIBUTE = NovelGatewayTrace.class.getName();
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final String id;
    private final boolean invalid;
    private final long startedNanos = System.nanoTime();
    private Double authDispatchMs;
    private volatile Double upstreamHeadersMs;
    private Double upstreamMs;
    private Double validationMs;

    private NovelGatewayTrace(String value, boolean multiple) {
        invalid = multiple || (value != null && !UUID_PATTERN.matcher(value).matches());
        id = value == null || invalid ? UUID.randomUUID().toString() : value;
    }

    public static NovelGatewayTrace forRequest(HttpServletRequest request) {
        Object existing = request.getAttribute(ATTRIBUTE);
        if (existing instanceof NovelGatewayTrace trace) {
            return trace;
        }
        var values = Collections.list(request.getHeaders(HEADER));
        var trace = new NovelGatewayTrace(values.isEmpty() ? null : values.getFirst(), values.size() > 1);
        request.setAttribute(ATTRIBUTE, trace);
        return trace;
    }

    static NovelGatewayTrace current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return forRequest(attributes.getRequest());
        }
        return new NovelGatewayTrace(null, false);
    }

    void requireValid() {
        // 인증은 기존 보안 경계에서 먼저 처리한다. 잘못된 ID는 유료 중계 전에 거절한다.
        if (invalid) {
            throw new NovelGatewayException(400, "NOVEL_TRACE_INVALID", false);
        }
    }

    void markDispatch() { authDispatchMs = elapsedMs(); }
    void markUpstreamHeaders(long start) { upstreamHeadersMs = since(start); }
    void markUpstreamComplete(long start) { upstreamMs = since(start); }
    void markUpstreamIncomplete(long start) {
        if (upstreamMs == null) {
            upstreamMs = since(start);
        }
    }
    void markValidation(long start) { validationMs = since(start); }

    public String id() { return id; }
    public double elapsedMs() { return since(startedNanos); }
    public Double authDispatchMs() { return authDispatchMs; }
    public Double upstreamHeadersMs() { return upstreamHeadersMs; }
    public Double upstreamMs() { return upstreamMs; }
    public Double validationMs() { return validationMs; }

    private static double since(long start) { return (System.nanoTime() - start) / 1_000_000.0; }
}
