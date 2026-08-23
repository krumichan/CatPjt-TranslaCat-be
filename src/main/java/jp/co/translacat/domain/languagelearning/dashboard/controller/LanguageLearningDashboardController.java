package jp.co.translacat.domain.languagelearning.dashboard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import jp.co.translacat.domain.languagelearning.dashboard.dto.response.DashboardResponseDto;
import jp.co.translacat.domain.languagelearning.dashboard.service.LanguageLearningDashboardQueryService;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/language-learning/dashboard")
@RequiredArgsConstructor
public class LanguageLearningDashboardController {

    private final LanguageLearningDashboardQueryService dashboardQueryService;

    @Operation(
            summary = "Language Learning Dashboard",
            description = "통합 언어 능력, 학습별 성과, 성장, 약점, 추천과 Source/Task별 Trend를 반환합니다."
    )
    @GetMapping
    public ResponseDto<DashboardResponseDto> get(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "조회 시작일. 기본값은 종료일 기준 30일 전")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "조회 종료일. 기본값은 사용자 Timezone의 오늘")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "ALL, WRITING, SPEAKING, LISTENING, READING")
            @RequestParam(defaultValue = "ALL") String source,
            @Parameter(description = "Listening Trend를 Task 단위로 제한할 때 사용")
            @RequestParam(required = false) ListeningTaskType taskType
    ) {
        return ResponseUtil.ok(
                dashboardQueryService.get(
                        SecurityUtil.getLoginUserId(userPrincipal),
                        from,
                        to,
                        source,
                        taskType
                )
        );
    }
}
