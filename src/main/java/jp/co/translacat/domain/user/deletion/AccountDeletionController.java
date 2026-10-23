package jp.co.translacat.domain.user.deletion;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class AccountDeletionController {
    private final AccountDeletionService deletion;
    private final AccountDeletionReauthentication reauthentication;
    private final JWTService jwt;
    private final ObjectMapper json = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public AccountDeletionController(AccountDeletionService deletion, AccountDeletionReauthentication reauthentication, JWTService jwt) {
        this.deletion = deletion;
        this.reauthentication = reauthentication;
        this.jwt = jwt;
    }

    @PostMapping(value = "/api/v1/users/me/deletion-reauthentication/password", consumes = "application/json")
    public ResponseEntity<?> password(@AuthenticationPrincipal UserPrincipal principal, HttpServletRequest request) {
        var actor = actor(principal, request);
        return ok(reauthentication.password(actor.userId(), actor.sid(), read(request, Password.class).password()));
    }

    @PostMapping(value = "/api/v1/users/me/deletion-reauthentication/google/start", consumes = "application/json")
    public ResponseEntity<?> googleStart(@AuthenticationPrincipal UserPrincipal principal, HttpServletRequest request) {
        var actor = actor(principal, request);
        read(request, Empty.class);
        return ok(reauthentication.startGoogle(actor.userId(), actor.sid()));
    }

    @PostMapping(value = "/api/v1/users/me/deletion-reauthentication/google/complete", consumes = "application/json")
    public ResponseEntity<?> googleComplete(@AuthenticationPrincipal UserPrincipal principal, HttpServletRequest request) {
        var actor = actor(principal, request);
        var body = read(request, Google.class);
        return ok(reauthentication.completeGoogle(actor.userId(), actor.sid(), body.intentToken(), body.idToken()));
    }

    @PostMapping(value = "/api/v1/users/me/deletion-requests", consumes = "application/json")
    public ResponseEntity<?> request(@AuthenticationPrincipal UserPrincipal principal, HttpServletRequest request) {
        var actor = actor(principal, request);
        return ok(deletion.request(actor.userId(), actor.sid(), read(request, AccountDeletionService.Request.class)));
    }

    @PostMapping(value = "/api/v1/account-deletion/status", consumes = "application/json")
    public ResponseEntity<?> status(HttpServletRequest request) {
        return ok(deletion.status(read(request, AccountDeletionService.StatusRequest.class), request.getRemoteAddr()));
    }

    private Actor actor(UserPrincipal principal, HttpServletRequest request) {
        if (principal == null || !principal.isEnabled()) throw DeletionTokens.invalid();
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() > 8200) throw DeletionTokens.invalid();
        String sid = jwt.getSessionId(authorization.substring(7));
        return new Actor(principal.getId(), DeletionTokens.uuid(sid));
    }

    private <T> T read(HttpServletRequest request, Class<T> type) {
        // actorId 추가, 중복 JSON 키와 초과 본문을 거부하고 parser의 원문 예외는 외부로 보내지 않는다.
        try {
            byte[] bytes = request.getInputStream().readNBytes(24 * 1024 + 1);
            if (bytes.length > 24 * 1024) throw DeletionTokens.invalid();
            T value = json.readValue(bytes, type);
            if (value == null) throw DeletionTokens.invalid();
            return value;
        } catch (IOException ignored) {
            throw DeletionTokens.invalid();
        }
    }

    private ResponseEntity<?> ok(Object value) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ResponseUtil.ok(value));
    }

    private record Actor(long userId, String sid) { }
    public record Password(String password) { }
    public record Google(String intentToken, String idToken) { }
    public record Empty() { }
}
