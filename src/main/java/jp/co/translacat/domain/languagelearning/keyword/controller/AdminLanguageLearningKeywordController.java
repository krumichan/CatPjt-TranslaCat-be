package jp.co.translacat.domain.languagelearning.keyword.controller;

import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordCreateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.KeywordResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.facade.LanguageLearningKeywordFacade;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.SecurityUtil;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/language-learning/system-keywords")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminLanguageLearningKeywordController {

    private final LanguageLearningKeywordFacade keywordFacade;

    @GetMapping
    public ResponseDto<List<KeywordResponseDto>> getSystemKeywords(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseUtil.ok(
                keywordFacade.getSystemKeywordsForAdmin(SecurityUtil.getLoginUserId(userPrincipal))
        );
    }

    @PostMapping
    public ResponseDto<KeywordResponseDto> createSystemKeyword(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody KeywordCreateRequestDto request
    ) {
        return ResponseUtil.created(
                keywordFacade.createSystemKeyword(SecurityUtil.getLoginUserId(userPrincipal), request)
        );
    }

    @PatchMapping("/{keywordId}")
    public ResponseDto<KeywordResponseDto> updateSystemKeyword(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long keywordId,
            @RequestBody KeywordUpdateRequestDto request
    ) {
        return ResponseUtil.ok(
                keywordFacade.updateSystemKeyword(SecurityUtil.getLoginUserId(userPrincipal), keywordId, request)
        );
    }
}
