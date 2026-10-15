package jp.co.translacat.domain.languagelearning.webai;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningWebAiClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/language-learning/web-ai")
public class WebAiLearningController {
    private final LanguageLearningWebAiClient client;
    private final WebAiConnectionPresenter connections;

    public WebAiLearningController(LanguageLearningWebAiClient client, WebAiConnectionPresenter connections) {
        this.client = client;
        this.connections = connections;
    }

    @GetMapping("/plans")
    public ResponseDto<JsonNode> plans(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseUtil.ok(client.plans(SecurityUtil.getLoginUserId(principal)));
    }

    @PostMapping("/plans")
    public ResponseDto<JsonNode> createPlan(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody JsonNode body) {
        return ResponseUtil.ok(client.createPlan(SecurityUtil.getLoginUserId(principal), body));
    }

    @PutMapping("/plans/{planId}")
    public ResponseDto<JsonNode> updatePlan(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable String planId, @RequestBody JsonNode body) {
        return ResponseUtil.ok(client.updatePlan(SecurityUtil.getLoginUserId(principal), planId, body));
    }

    @DeleteMapping("/plans/{planId}")
    public ResponseEntity<Void> deletePlan(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String planId) {
        client.deletePlan(SecurityUtil.getLoginUserId(principal), planId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/plans/{planId}/connection")
    public ResponseDto<JsonNode> connect(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String planId) {
        return ResponseUtil.ok(connections.present(client.connect(SecurityUtil.getLoginUserId(principal), planId)));
    }

    @DeleteMapping("/plans/{planId}/connection")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String planId) {
        client.revoke(SecurityUtil.getLoginUserId(principal), planId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/plans/{planId}/runs")
    public ResponseDto<JsonNode> createRun(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable String planId, @RequestBody JsonNode body) {
        return ResponseUtil.ok(client.createRun(SecurityUtil.getLoginUserId(principal), planId, body));
    }

    @GetMapping("/runs")
    public ResponseDto<JsonNode> runs(@AuthenticationPrincipal UserPrincipal principal,
                                    @RequestParam(defaultValue = "20") int limit) {
        return ResponseUtil.ok(client.runs(SecurityUtil.getLoginUserId(principal), limit));
    }

    @GetMapping("/runs/{runId}")
    public ResponseDto<JsonNode> run(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String runId) {
        return ResponseUtil.ok(client.run(SecurityUtil.getLoginUserId(principal), runId));
    }

    @PostMapping("/results")
    public ResponseDto<JsonNode> submit(@AuthenticationPrincipal UserPrincipal principal, @RequestBody JsonNode body) {
        return ResponseUtil.ok(client.submit(SecurityUtil.getLoginUserId(principal), body));
    }

    @GetMapping("/results")
    public ResponseDto<JsonNode> results(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestParam(defaultValue = "20") int limit) {
        return ResponseUtil.ok(client.results(SecurityUtil.getLoginUserId(principal), limit));
    }

    @GetMapping("/results/{resultId}")
    public ResponseDto<JsonNode> result(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String resultId) {
        return ResponseUtil.ok(client.result(SecurityUtil.getLoginUserId(principal), resultId));
    }

    @PostMapping("/results/{resultId}/retry")
    public ResponseDto<JsonNode> retry(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String resultId) {
        return ResponseUtil.ok(client.retry(SecurityUtil.getLoginUserId(principal), resultId));
    }
}
