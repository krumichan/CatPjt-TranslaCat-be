package jp.co.translacat.domain.languagelearning.history.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.history.dto.response.LearningHistoryDetailResponseDto;
import jp.co.translacat.domain.languagelearning.history.dto.response.LearningHistoryItemResponseDto;
import jp.co.translacat.domain.languagelearning.history.service.LearningHistoryQueryService;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/language-learning/history")
@RequiredArgsConstructor
public class LearningHistoryController {

    private final LearningHistoryQueryService historyQueryService;

    @Operation(summary = "누적 학습 근거 조회", description = "저장된 근거와 기존 학습 연결을 읽습니다. 새 평가나 생성을 실행하지 않습니다.")
    @GetMapping("/evidence")
    public ResponseDto<JsonNode> getEvidence(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String learningLanguage,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String resultKind,
            @RequestParam(required = false) String policyVersion,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String limit
    ) {
        // 소유자는 기존 인증에서 얻고 조회 조건·정책 판정은 LL에 그대로 위임한다.
        return ResponseUtil.ok(historyQueryService.getEvidence(
                SecurityUtil.getLoginUserId(principal), source, learningLanguage, from, to,
                resultKind, policyVersion, cursor, limit
        ));
    }

    @Operation(
            summary = "Language Learning History 조회",
            description = "Source와 Listening Task Type으로 학습 이력을 필터링합니다."
    )
    @GetMapping
    public ResponseDto<List<LearningHistoryItemResponseDto>> getHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) LearningSource source,
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String status,
            @Parameter(description = "LISTENING Source에 적용할 Task Filter")
            @RequestParam(required = false) ListeningTaskType taskType
    ) {
        return ResponseUtil.ok(
                historyQueryService.getHistory(
                        SecurityUtil.getLoginUserId(principal),
                        source,
                        period,
                        status,
                        taskType
                )
        );
    }

    @Operation(
            summary = "Language Learning History 상세 조회",
            description = "Listening 상세에는 Reference/User Audio의 available, expired, retentionUntil, deletedAt 상태가 포함됩니다."
    )
    @GetMapping("/{activityId}")
    public ResponseDto<LearningHistoryDetailResponseDto> getDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String activityId
    ) {
        return ResponseUtil.ok(
                historyQueryService.getDetail(
                        SecurityUtil.getLoginUserId(principal),
                        activityId
                )
        );
    }
}
