package jp.co.translacat.domain.languagelearning.daily.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningWritingClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** 명시적으로 선택한 로컬 QA에서만 가변 N 경로를 인증된 owner로 연결한다. */
@RestController
@ConditionalOnProperty(name = "app.language-learning.writing-paragraph-qa-enabled", havingValue = "true")
@RequestMapping("/api/v1/language-learning/writing/curated/plans")
public class WritingPlanController {
    private final ObjectProvider<LanguageLearningWritingClient> clients;

    public WritingPlanController(ObjectProvider<LanguageLearningWritingClient> clients) {
        this.clients = clients;
    }

    @GetMapping("/config")
    public ResponseDto<JsonNode> config(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseUtil.ok(client().writingPlanConfig(SecurityUtil.getLoginUserId(principal)));
    }

    @PostMapping("/preview")
    public ResponseDto<JsonNode> preview(@AuthenticationPrincipal UserPrincipal principal, @RequestBody JsonNode body) {
        return ResponseUtil.ok(client().writingPlanPreview(SecurityUtil.getLoginUserId(principal), body));
    }

    @PostMapping
    public ResponseDto<JsonNode> start(@AuthenticationPrincipal UserPrincipal principal, @RequestBody JsonNode body) {
        return ResponseUtil.ok(client().writingPlanStart(SecurityUtil.getLoginUserId(principal), body));
    }

    @GetMapping("/{setId}")
    public ResponseDto<JsonNode> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long setId) {
        return ResponseUtil.ok(client().writingPlanGet(SecurityUtil.getLoginUserId(principal), setId));
    }

    @GetMapping("/history/{date}")
    public ResponseDto<JsonNode> history(@AuthenticationPrincipal UserPrincipal principal, @PathVariable LocalDate date,
                                         @RequestParam DailyWritingType writingType) {
        return ResponseUtil.ok(client().writingPlanHistory(SecurityUtil.getLoginUserId(principal), date, writingType));
    }

    @PostMapping("/{setId}/target")
    public ResponseDto<JsonNode> target(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long setId,
                                        @RequestBody JsonNode body) {
        return ResponseUtil.ok(client().writingPlanExpand(SecurityUtil.getLoginUserId(principal), setId, body));
    }

    @PostMapping("/{setId}/restore")
    public ResponseDto<JsonNode> restore(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long setId,
                                         @RequestBody JsonNode body) {
        return ResponseUtil.ok(client().writingPlanRestore(SecurityUtil.getLoginUserId(principal), setId, body));
    }

    @PostMapping("/items/{itemId}/answers")
    public ResponseDto<JsonNode> answer(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long itemId,
                                        @RequestBody JsonNode body) {
        return ResponseUtil.ok(client().writingPlanAnswer(SecurityUtil.getLoginUserId(principal), itemId, body));
    }

    @PostMapping("/answers/{answerId}/feedback/retry")
    public ResponseDto<JsonNode> retryFeedback(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long answerId) {
        return ResponseUtil.ok(client().writingPlanFeedbackRetry(SecurityUtil.getLoginUserId(principal), answerId));
    }

    private LanguageLearningWritingClient client() {
        var client = clients.getIfAvailable();
        if (client == null) throw new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                "LL_WRITING_REMOTE_DISABLED", "Writing 서비스 연결이 비활성화되어 있습니다.");
        return client;
    }
}
