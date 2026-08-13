package jp.co.translacat.domain.languagelearning.keyword.controller;

import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordCreateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.SystemKeywordSelectionRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.KeywordListResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.KeywordResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.facade.LanguageLearningKeywordFacade;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/language-learning/keywords")
@RequiredArgsConstructor
public class LanguageLearningKeywordController {

    private final LanguageLearningKeywordFacade keywordFacade;

    @GetMapping
    public ResponseDto<KeywordListResponseDto> getKeywords(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return ResponseUtil.ok(
                keywordFacade.getKeywords(
                        SecurityUtil.getLoginUserId(userPrincipal)
                )
        );
    }

    @PostMapping("/custom")
    public ResponseDto<KeywordResponseDto> createCustomKeyword(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody KeywordCreateRequestDto request
    ) {
        return ResponseUtil.created(
                keywordFacade.createCustomKeyword(
                        SecurityUtil.getLoginUserId(userPrincipal),
                        request
                )
        );
    }

    @PatchMapping("/custom/{keywordId}")
    public ResponseDto<KeywordResponseDto> updateCustomKeyword(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long keywordId,
            @RequestBody KeywordUpdateRequestDto request
    ) {
        return ResponseUtil.ok(
                keywordFacade.updateCustomKeyword(
                        SecurityUtil.getLoginUserId(userPrincipal),
                        keywordId,
                        request
                )
        );
    }

    @DeleteMapping("/custom/{keywordId}")
    public ResponseDto<Boolean> deleteCustomKeyword(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long keywordId
    ) {
        keywordFacade.deleteCustomKeyword(
                SecurityUtil.getLoginUserId(userPrincipal),
                keywordId
        );
        return ResponseUtil.ok(true);
    }

    @PutMapping("/system/{keywordId}/selection")
    public ResponseDto<KeywordResponseDto> updateSystemKeywordSelection(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long keywordId,
            @RequestBody SystemKeywordSelectionRequestDto request
    ) {
        return ResponseUtil.ok(
                keywordFacade.updateSystemKeywordSelection(
                        SecurityUtil.getLoginUserId(userPrincipal),
                        keywordId,
                        request.selected()
                )
        );
    }
}
