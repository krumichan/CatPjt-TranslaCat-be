package jp.co.translacat.domain.languagelearning.dashboard.controller;

import jp.co.translacat.domain.languagelearning.dashboard.dto.response.DashboardResponseDto;
import jp.co.translacat.domain.languagelearning.dashboard.service.LanguageLearningDashboardQueryService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/language-learning/dashboard")
@RequiredArgsConstructor
public class LanguageLearningDashboardController {

    private final LanguageLearningDashboardQueryService dashboardQueryService;

    @GetMapping
    public ResponseDto<DashboardResponseDto> get(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(defaultValue = "7d") String period,
            @RequestParam(defaultValue = "ALL") String source
    ) {
        return ResponseUtil.ok(
                dashboardQueryService.get(
                        SecurityUtil.getLoginUserId(userPrincipal),
                        period,
                        source
                )
        );
    }
}
