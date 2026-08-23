package jp.co.translacat.domain.languagelearning.history.controller;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/language-learning/history")
@RequiredArgsConstructor
public class LearningHistoryController {

    private final LearningHistoryQueryService historyQueryService;

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
