package jp.co.translacat.domain.user.pushinstallation;

import jakarta.servlet.http.HttpServletRequest;
import jp.co.translacat.infrastructure.chat.core.ChatCoreServicePrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Set;

@RestController
public class PushInstallationInternalController {
    private final PushInstallationService service;

    public PushInstallationInternalController(PushInstallationService service) { this.service = service; }

    @PostMapping("/internal/v1/chat/push-targets/query")
    public ResponseEntity<?> targets(@AuthenticationPrincipal ChatCoreServicePrincipal caller, HttpServletRequest request) {
        requireScope(caller, "chat:push-targets:read");
        var body = PushInstallationValidation.body(request, Set.of("userIds", "appEnvironment", "projectId"));
        if (!body.path("userIds").isArray() || body.path("userIds").size() > 100) throw PushInstallationException.invalid();
        var users = new ArrayList<Long>();
        for (var id : body.path("userIds")) {
            if (!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0) throw PushInstallationException.invalid();
            users.add(id.longValue());
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.targets(users,
                PushInstallationValidation.text(body, "appEnvironment"), PushInstallationValidation.text(body, "projectId")));
    }

    @PostMapping("/internal/v1/chat/push-installations/invalidate")
    public ResponseEntity<Void> invalidate(@AuthenticationPrincipal ChatCoreServicePrincipal caller, HttpServletRequest request) {
        requireScope(caller, "chat:push-installations:invalidate");
        var body = PushInstallationValidation.body(request, Set.of("installationId", "accountBindingId", "generation", "tokenVersion"));
        service.invalidate(new PushInstallationDtos.Invalidate(PushInstallationValidation.text(body, "installationId"),
                PushInstallationValidation.text(body, "accountBindingId"), PushInstallationValidation.number(body, "generation"),
                PushInstallationValidation.number(body, "tokenVersion")));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private void requireScope(ChatCoreServicePrincipal caller, String scope) {
        if (caller == null || !scope.equals(caller.scope())) throw PushInstallationException.unauthorized();
    }
}
