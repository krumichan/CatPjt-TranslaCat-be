package jp.co.translacat.domain.user.pushinstallation;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/users/me/push-installations")
public class PushInstallationController {
    private final PushInstallationService service;
    private final JWTService jwt;

    public PushInstallationController(PushInstallationService service, JWTService jwt) {
        this.service = service;
        this.jwt = jwt;
    }

    @PutMapping("/{installationId}")
    public ResponseEntity<?> put(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String installationId,
                                 HttpServletRequest request) {
        var actor = actor(principal, request);
        var body = PushInstallationValidation.body(request, Set.of("provider", "expoPushToken", "platform",
                "appEnvironment", "projectId", "installationProof", "generation"));
        var input = new PushInstallationDtos.Put(PushInstallationValidation.text(body, "provider"),
                PushInstallationValidation.text(body, "expoPushToken"), PushInstallationValidation.text(body, "platform"),
                PushInstallationValidation.text(body, "appEnvironment"), PushInstallationValidation.text(body, "projectId"),
                PushInstallationValidation.text(body, "installationProof"), PushInstallationValidation.number(body, "generation"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ResponseUtil.ok(service.put(actor, installationId, input)));
    }

    @DeleteMapping("/{installationId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String installationId,
                                       HttpServletRequest request) {
        var actor = actor(principal, request);
        var body = PushInstallationValidation.body(request, Set.of("installationProof", "generation"));
        service.delete(actor, installationId, new PushInstallationDtos.Delete(PushInstallationValidation.text(body, "installationProof"),
                PushInstallationValidation.number(body, "generation")));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private PushInstallationDtos.Actor actor(UserPrincipal principal, HttpServletRequest request) {
        var headers = Collections.list(request.getHeaders("Authorization"));
        if (principal == null || principal.getId() == null || headers.size() != 1
                || !headers.getFirst().startsWith("Bearer ") || headers.getFirst().length() > 8199) {
            throw PushInstallationException.unauthorized();
        }
        try {
            String token = headers.getFirst().substring(7);
            Long id = jwt.getAccessId(token);
            String sid = jwt.getSessionId(token);
            if (!principal.getId().equals(id) || !PushInstallationValidation.isUuid(sid)) throw PushInstallationException.unauthorized();
            return new PushInstallationDtos.Actor(id, sid);
        } catch (JwtException | IllegalArgumentException ignored) {
            throw PushInstallationException.unauthorized();
        }
    }
}
