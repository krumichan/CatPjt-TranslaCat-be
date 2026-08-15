package jp.co.translacat.domain.languagelearning.setting.controller;

import jp.co.translacat.domain.languagelearning.setting.dto.request.AdminSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.AdminSettingResponseDto;
import jp.co.translacat.domain.languagelearning.setting.facade.AdminLanguageLearningSettingFacade;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/language-learning/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminLanguageLearningSettingController {

    private final AdminLanguageLearningSettingFacade settingFacade;

    @GetMapping
    public ResponseDto<AdminSettingResponseDto> get() {
        return ResponseUtil.ok(settingFacade.get());
    }

    @PatchMapping
    public ResponseDto<AdminSettingResponseDto> update(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody AdminSettingUpdateRequestDto request
    ) {
        return ResponseUtil.ok(
                settingFacade.update(userPrincipal.getId(), request)
        );
    }
}
