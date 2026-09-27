package jp.co.translacat.infrastructure.chat.core;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
public class ChatCoreIdentityController {
    private static final int MAX_BODY_BYTES = 16 * 1024;
    private final UserRepository users;
    private final ObjectMapper mapper = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    public ChatCoreIdentityController(UserRepository users) {
        this.users = users;
    }

    @PostMapping(value = "/internal/v1/chat/identity", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> identity(@AuthenticationPrincipal ChatCorePrincipal caller, HttpServletRequest request) {
        // 최대 크기를 읽으면서 제한하고 요청 사용자 ID와 서비스 토큰의 주체를 먼저 결합한다.
        JsonNode body;
        try {
            byte[] bytes = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) return rejected(400);
            body = mapper.readTree(bytes);
        } catch (IOException ignored) {
            return rejected(400);
        }
        if (caller == null || body == null || !body.isObject() || body.size() != 2
                || !body.path("subject").isTextual() || body.path("subject").textValue().isBlank()
                || body.path("subject").textValue().length() > 320
                || !body.path("tokenUserId").isIntegralNumber() || !body.path("tokenUserId").canConvertToLong()) {
            return rejected(400);
        }
        long userId = body.path("tokenUserId").longValue();
        if (userId <= 0 || userId != caller.userId()) return rejected(403);

        // 계정 소유 저장소의 현재 ID/email/role만 조회한다. get-or-create, profile 생성, 계정 복제는 없다.
        String email = body.path("subject").textValue();
        var user = users.findByEmail(email);
        if (user.isEmpty() || user.get().getId() == null || user.get().getId() != userId
                || !email.equals(user.get().getEmail()) || user.get().getAuthority() == null) {
            return rejected(404);
        }
        var principal = new UserPrincipal(user.get());
        // 현재 원본의 상태 flags는 모두 true다. 존재하지 않는 정지/탈퇴 DB 정책을 새로 만들지 않는다.
        boolean canAuthenticate = principal.isEnabled() && principal.isAccountNonLocked()
                && principal.isAccountNonExpired() && principal.isCredentialsNonExpired();
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(
                new IdentityResponse(userId, principal.getUsername(), user.get().getAuthority().value(), canAuthenticate));
    }

    private static ResponseEntity<?> rejected(int status) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store")
                .body(Map.of("code", "CHAT_CORE_IDENTITY_REJECTED"));
    }

    public record IdentityResponse(long userId, String email, String role, boolean canAuthenticate) {
    }
}
