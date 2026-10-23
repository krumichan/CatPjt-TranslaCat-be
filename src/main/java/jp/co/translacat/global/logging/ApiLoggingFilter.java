package jp.co.translacat.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
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

        // Auth contains passwords and bearer/refresh tokens. Voice contains transcripts,
        // translations and short-lived tickets. Log metadata only for both surfaces.
        String redactionLabel = sensitiveBodyLabel(request.getRequestURI());
        if (redactionLabel == null) {
            redactionLabel = sensitiveBodyLabel(request.getServletPath());
        }
        // servlet이 해석한 동일 push 경로도 가린다. URL을 직접/중복 decode하지 않는다.
        if (isPushInstallationPath(request.getServletPath())) {
            redactionLabel = "PUSH_INSTALLATION_REDACTED";
        }
        if (isInternalPushPath(request.getServletPath())) {
            redactionLabel = "PUSH_INTERNAL_REDACTED";
        }
        if (redactionLabel != null) {
            // 민감 경로는 본문과 함께 인증 주체의 원문도 로그에서 제외한다.
            String loggedUri = switch (redactionLabel) {
                case "PUSH_INSTALLATION_REDACTED" -> "/api/v1/users/me/push-installations/[REDACTED]";
                case "PUSH_INTERNAL_REDACTED" -> "/internal/v1/chat/push/[REDACTED]";
                default -> request.getRequestURI();
            };
            log.info(
                    "[REQUEST] [{}] {} {} | Body: [{}]",
                    "REDACTED",
                    request.getMethod(),
                    loggedUri,
                    redactionLabel
            );
            try {
                chain.doFilter(request, response);
            } finally {
                log.info(
                        "[RESPONSE] Status: {} | Body: [{}]",
                        response.getStatus(),
                        redactionLabel
                );
            }
            return;
        }

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

    private String sensitiveBodyLabel(String uri) {
        // receipt/proof/Google 증명과 접수 응답은 원문 및 사용자 식별자를 모두 가린다.
        if (uri != null && (uri.startsWith("/api/v1/users/me/deletion-")
                || uri.equals("/api/v1/account-deletion/status"))) {
            return "ACCOUNT_DELETION_REDACTED";
        }
        // 푸시 설치 proof와 provider token은 공개 등록 요청/응답 로그에서 제외한다.
        if (isPushInstallationPath(uri)) {
            return "PUSH_INSTALLATION_REDACTED";
        }
        // 내부 target 응답의 provider token도 raw/decoded 경로 모두에서 본문·URI를 가린다.
        if (isInternalPushPath(uri)) {
            return "PUSH_INTERNAL_REDACTED";
        }
        // 연결 응답의 비밀 토큰과 학습 원문·답안·전체 피드백을 요청 및 응답 로그에서 제외한다.
        if (uri.equals("/api/v1/language-learning/web-ai")
                || uri.startsWith("/api/v1/language-learning/web-ai/")) {
            return "WEB_AI_REDACTED";
        }
        // 비공개 문의·개인 메시지·수신자 검색에는 본문과 계정 정보를 로그에 남기지 않는다.
        if (uri.equals("/api/v1/support") || uri.startsWith("/api/v1/support/")) {
            return "SUPPORT_REDACTED";
        }

        // 회차 원문/번역/음성은 긴 본문·개인 읽기 정보를 포함하므로 메타데이터만 기록한다.
        if (uri.matches("/api/v1/[^/]+/[^/]+/episodes(?:/.*)?")) {
            return "NOVEL_REDACTED";
        }
        // Chat 메시지/이미지/프로필과 WebSocket handshake도 본문·사용자 식별자를 기록하지 않는다.
        if (uri.equals("/api/v1/chat") || uri.startsWith("/api/v1/chat/")
                || uri.equals("/api/v1/admin/chat") || uri.startsWith("/api/v1/admin/chat/")
                || uri.equals("/api/v1/users/me/chat-language-settings")
                || uri.equals("/ws/chat") || uri.startsWith("/ws/chat/")) {
            return "CHAT_REDACTED";
        }
        // 현재 identity의 subject/email과 내부 요청 본문은 로그에 남기지 않는다.
        if (uri.equals("/internal/v1/chat") || uri.startsWith("/internal/v1/chat/")) {
            return "CHAT_INTERNAL_REDACTED";
        }
        if (uri.startsWith("/api/v1/auth")) {
            return "AUTH_REDACTED";
        }
        if (uri.startsWith("/api/v1/voice/")) {
            return "VOICE_REDACTED";
        }
        // Writing 응답에는 학습자 답변과 원문이 포함되므로 본문을 기록하지 않는다.
        if (uri.startsWith("/api/v1/language-learning/writing/")) {
            return "WRITING_REDACTED";
        }
        return null;
    }

    private boolean isPushInstallationPath(String path) {
        return path != null && (path.equals("/api/v1/users/me/push-installations")
                || path.startsWith("/api/v1/users/me/push-installations/"));
    }

    private boolean isInternalPushPath(String path) {
        return "/internal/v1/chat/push-targets/query".equals(path)
                || "/internal/v1/chat/push-installations/invalidate".equals(path);
    }
}
