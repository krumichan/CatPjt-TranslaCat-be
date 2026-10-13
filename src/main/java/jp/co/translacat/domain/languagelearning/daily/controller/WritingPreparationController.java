package jp.co.translacat.domain.languagelearning.daily.controller;

import com.fasterxml.jackson.databind.JsonNode;
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

/** 기존 paragraph QA opt-in 안에서만 후보 준비·조회 경로를 노출한다. */
@RestController
@ConditionalOnProperty(name = "app.language-learning.writing-paragraph-qa-enabled", havingValue = "true")
@RequestMapping("/api/v1/language-learning/writing/curated/preparations")
public class WritingPreparationController {
    private final ObjectProvider<LanguageLearningWritingClient> clients;

    public WritingPreparationController(ObjectProvider<LanguageLearningWritingClient> clients) {
        this.clients = clients;
    }

    @PostMapping
    public ResponseDto<JsonNode> start(@AuthenticationPrincipal UserPrincipal principal, @RequestBody JsonNode body) {
        return ResponseUtil.ok(client().writingPreparationStart(SecurityUtil.getLoginUserId(principal), body));
    }

    @GetMapping("/{requestKey}")
    public ResponseDto<JsonNode> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String requestKey) {
        return ResponseUtil.ok(client().writingPreparationGet(SecurityUtil.getLoginUserId(principal), requestKey));
    }

    private LanguageLearningWritingClient client() {
        var client = clients.getIfAvailable();
        if (client == null) throw new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                "LL_WRITING_REMOTE_DISABLED", "Writing 서비스 연결이 비활성화되어 있습니다.");
        return client;
    }
}
