package jp.co.translacat.domain.languagelearning.webai;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningWebAiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Forwards the authenticated user or administrator to the LL plan and quota endpoints. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/language-learning/auto-practice")
public class AutoPracticeController {
    private final LanguageLearningWebAiClient client;

    /** Returns the authenticated learner's plans and saved assignments. */
    @GetMapping
    public ResponseDto<JsonNode> workspace(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseUtil.ok(client.autoPractice(SecurityUtil.getLoginUserId(principal)));
    }

    /** Creates a saved plan and applies its activation preference. */
    @PostMapping("/plans")
    public ResponseDto<JsonNode> create(@AuthenticationPrincipal UserPrincipal principal, @RequestBody JsonNode body) {
        return ResponseUtil.ok(client.saveAutoPractice(SecurityUtil.getLoginUserId(principal), null, body));
    }

    /** Updates a plan using its current revision. */
    @PutMapping("/plans/{planId}")
    public ResponseDto<JsonNode> update(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable String planId, @RequestBody JsonNode body) {
        return ResponseUtil.ok(client.saveAutoPractice(SecurityUtil.getLoginUserId(principal), planId, body));
    }

    /** Removes an owned plan while preserving prepared assignments. */
    @DeleteMapping("/plans/{planId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable String planId) {
        client.deleteAutoPractice(SecurityUtil.getLoginUserId(principal), planId);
        return ResponseEntity.noContent().build();
    }

    /** Reads the complete set of administrator-configured limits. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/limits")
    public ResponseDto<JsonNode> limits(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseUtil.ok(client.autoPracticeLimits(SecurityUtil.getLoginUserId(principal)));
    }

    /** Applies twelve validated limits atomically. */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/limits")
    public ResponseDto<JsonNode> saveLimits(
            @AuthenticationPrincipal UserPrincipal principal, @RequestBody JsonNode body) {
        return ResponseUtil.ok(client.saveAutoPracticeLimits(SecurityUtil.getLoginUserId(principal), body));
    }
}
