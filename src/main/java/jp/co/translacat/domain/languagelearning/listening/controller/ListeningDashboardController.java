package jp.co.translacat.domain.languagelearning.listening.controller;

import io.swagger.v3.oas.annotations.Operation;

import jp.co.translacat.domain.languagelearning.listening.dashboard.facade.ListeningDashboardFacade;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/language-learning")
@RequiredArgsConstructor
public class ListeningDashboardController {

    private final ListeningDashboardFacade facade;

    @Operation(summary = "학습 추천 숨김")
    @PostMapping("/recommendations/{recommendationId}/dismiss")
    public ResponseDto<Void> dismiss(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long recommendationId
    ) {
        facade.dismiss(
                SecurityUtil.getLoginUserId(principal),
                recommendationId
        );
        return ResponseUtil.noContent();
    }

    @Operation(summary = "Listening 운영 정책 조회")
    @GetMapping("/listening/policy")
    public ResponseDto<ListeningApiContract.PolicyView> policy() {
        return ResponseUtil.ok(facade.policy());
    }
}
