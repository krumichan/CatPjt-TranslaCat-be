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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** 인증된 사용자 ID만 LL에 전달하는 신규 비점수 Writing 중계. */
@RestController
@RequestMapping("/api/v1/language-learning/writing/curated")
public class CuratedWritingController {
    private final ObjectProvider<LanguageLearningWritingClient> clients;

    public CuratedWritingController(ObjectProvider<LanguageLearningWritingClient> clients) {
        this.clients = clients;
    }

    @PostMapping("/sets")
    public ResponseDto<JsonNode> start(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestBody StartRequest request) {
        return ResponseUtil.ok(client().curatedStart(SecurityUtil.getLoginUserId(principal),
                request.writingType(), request.rePractice()));
    }

    @GetMapping("/sets/{setId}")
    public ResponseDto<JsonNode> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long setId) {
        return ResponseUtil.ok(client().curatedGet(SecurityUtil.getLoginUserId(principal), setId));
    }

    @GetMapping("/history/{date}")
    public ResponseDto<JsonNode> history(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable LocalDate date,
                                         @RequestParam DailyWritingType writingType) {
        return ResponseUtil.ok(client().curatedHistory(SecurityUtil.getLoginUserId(principal), date, writingType));
    }

    @PostMapping("/items/{itemId}/answers")
    public ResponseDto<JsonNode> answer(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long itemId, @RequestBody AnswerRequest request) {
        return ResponseUtil.ok(client().curatedSubmit(SecurityUtil.getLoginUserId(principal),
                itemId, request.answer(), request.contentRevision()));
    }

    @PostMapping("/sets/{setId}/replace")
    public ResponseDto<JsonNode> replace(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable Long setId, @RequestBody ReplaceRequest request) {
        return ResponseUtil.ok(client().curatedReplace(SecurityUtil.getLoginUserId(principal),
                setId, request.rePractice()));
    }

    private LanguageLearningWritingClient client() {
        var value = clients.getIfAvailable();
        if (value == null) throw new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                "LL_WRITING_REMOTE_DISABLED", "Writing 서비스 연결이 비활성화되어 있습니다.");
        return value;
    }

    public record StartRequest(DailyWritingType writingType, boolean rePractice) { }
    public record AnswerRequest(String answer, String contentRevision) { }
    public record ReplaceRequest(boolean rePractice) { }
}
