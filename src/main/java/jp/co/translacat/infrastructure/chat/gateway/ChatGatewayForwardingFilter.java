package jp.co.translacat.infrastructure.chat.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jp.co.translacat.global.security.UserPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class ChatGatewayForwardingFilter extends OncePerRequestFilter {
    private static final int MAX_REQUEST_BYTES = 20 * 1024 * 1024;
    private final ChatGatewayProperties properties;
    private final ChatGatewayTarget target;
    private final ChatGatewayTokenIssuer issuer;

    ChatGatewayForwardingFilter(ChatGatewayProperties properties, ChatGatewayTarget target, ChatGatewayTokenIssuer issuer) {
        this.properties = properties;
        this.target = target;
        this.issuer = issuer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!ChatGatewayTarget.isChatHttpPath(request.getServletPath())) {
            chain.doFilter(request, response);
            return;
        }

        // 이 filter는 AuthorizationFilter 이후에만 실행한다. MVC multipart parsing 전에 원본 바이트를 전달한다.
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal user)) {
            response.setStatus(401);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            fail(response, 413, "CHAT_GATEWAY_REQUEST_TOO_LARGE");
            return;
        }

        try {
            String scope = request.getServletPath().startsWith("/api/v1/admin/chat") ? "chat:admin" : "chat:http";
            var upstream = HttpRequest.newBuilder(target.httpUri(request.getRequestURI(), request.getQueryString()))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Authorization", request.getHeader("Authorization"))
                    .header("X-Chat-Service-Authorization", "Bearer " + issuer.issue(user.getId(), scope));
            for (String name : List.of("Content-Type", "Accept", "Accept-Language")) {
                String value = request.getHeader(name);
                if (value != null) upstream.header(name, value);
            }
            upstream.method(request.getMethod(), body.length == 0
                    ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));

            // Host, cookies, caller identity, forwarding, hop-by-hop 헤더는 복사하지 않는다. redirect도 따라가지 않는다.
            var result = sendWithinDeadline(upstream.build());
            if (result.statusCode() >= 300 && result.statusCode() < 400 && result.statusCode() != 304) {
                fail(response, 502, "CHAT_GATEWAY_REDIRECT_REJECTED");
                return;
            }
            response.setStatus(result.statusCode());
            for (String name : List.of("Content-Type", "Cache-Control", "ETag", "Last-Modified", "Retry-After")) {
                result.headers().firstValue(name).ifPresent(value -> response.setHeader(name, value));
            }
            response.getOutputStream().write(result.body());
        } catch (HttpTimeoutException | TimeoutException ignored) {
            fail(response, 504, "CHAT_GATEWAY_TIMEOUT");
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            fail(response, 502, "CHAT_GATEWAY_UNAVAILABLE");
        } catch (Exception ignored) {
            fail(response, 502, "CHAT_GATEWAY_UNAVAILABLE");
        }
    }

    private HttpResponse<byte[]> sendWithinDeadline(HttpRequest request)
            throws InterruptedException, IOException, TimeoutException {
        // JDK request timeout만으로는 headers 이후 지연된 body를 제한하지 못한다. 전체 future에 deadline을 둔다.
        var pending = target.httpClient().sendAsync(request, ignored -> new ChatGatewayBodySubscriber());
        try {
            return pending.get(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof HttpTimeoutException timeout) throw timeout;
            throw new IOException("Chat gateway upstream failed.");
        } finally {
            if (!pending.isDone()) pending.cancel(true);
        }
    }

    private static void fail(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }
}
