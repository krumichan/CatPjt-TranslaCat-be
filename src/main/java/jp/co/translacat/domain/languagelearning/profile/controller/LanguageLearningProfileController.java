package jp.co.translacat.domain.languagelearning.profile.controller;

import jp.co.translacat.domain.languagelearning.profile.dto.response.ProfileResponseDto;
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileQueryService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/language-learning/profile")
@RequiredArgsConstructor
public class LanguageLearningProfileController {

    private final LearningProfileQueryService profileQueryService;

    @GetMapping
    public ResponseDto<ProfileResponseDto> get(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return ResponseUtil.ok(
                profileQueryService.getProfile(
                        SecurityUtil.getLoginUserId(userPrincipal)
                )
        );
    }
}
