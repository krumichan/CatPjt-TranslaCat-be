package jp.co.translacat.domain.languagelearning.setting.controller;

import jp.co.translacat.domain.languagelearning.setting.dto.request.UserSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.UserSettingResponseDto;
import jp.co.translacat.domain.languagelearning.setting.facade.LanguageLearningSettingFacade;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/language-learning/settings")
@RequiredArgsConstructor
public class LanguageLearningSettingController {

    private final LanguageLearningSettingFacade settingFacade;

    @GetMapping
    public ResponseDto<UserSettingResponseDto> get(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return ResponseUtil.ok(
                settingFacade.get(
                        SecurityUtil.getLoginUserId(userPrincipal)
                )
        );
    }

    @PatchMapping
    public ResponseDto<UserSettingResponseDto> update(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody UserSettingUpdateRequestDto request
    ) {
        return ResponseUtil.ok(
                settingFacade.update(
                        SecurityUtil.getLoginUserId(userPrincipal),
                        request
                )
        );
    }
}
