package jp.co.translacat.domain.languagelearning.practice.controller;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.practice.dto.request.PracticeAnswerSubmitRequestDto;
import jp.co.translacat.domain.languagelearning.practice.dto.response.PracticeAnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.practice.dto.response.PracticeSetResponseDto;
import jp.co.translacat.domain.languagelearning.practice.dto.response.PracticeTodayModeStatusResponseDto;
import jp.co.translacat.domain.languagelearning.practice.dto.response.VocabularyMasterySummaryResponseDto;
import jp.co.translacat.domain.languagelearning.practice.service.PracticeFacade;
import jp.co.translacat.domain.languagelearning.practice.service.VocabularyMasteryQueryService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/language-learning/practice")
@RequiredArgsConstructor
public class ReadingVocabularyPracticeController {
    private final PracticeFacade practiceFacade;
    private final VocabularyMasteryQueryService masteryQueryService;

    @GetMapping("/today")
    public ResponseDto<PracticeSetResponseDto> getToday(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam PracticeDomain domain,
            @RequestParam String mode
    ) {
        return ResponseUtil.ok(practiceFacade.getToday(
                SecurityUtil.getLoginUserId(principal), domain, mode
        ));
    }

    @GetMapping("/today/status")
    public ResponseDto<List<PracticeTodayModeStatusResponseDto>> getTodayStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam PracticeDomain domain
    ) {
        return ResponseUtil.ok(practiceFacade.getTodayStatus(
                SecurityUtil.getLoginUserId(principal), domain
        ));
    }

    @GetMapping("/sets/{setId}")
    public ResponseDto<PracticeSetResponseDto> getSet(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long setId
    ) {
        return ResponseUtil.ok(practiceFacade.get(
                SecurityUtil.getLoginUserId(principal), setId
        ));
    }

    @PostMapping("/questions/{questionId}/answers")
    public ResponseDto<PracticeAnswerResultResponseDto> submit(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long questionId,
            @RequestBody PracticeAnswerSubmitRequestDto request
    ) {
        return ResponseUtil.ok(practiceFacade.submit(
                SecurityUtil.getLoginUserId(principal), questionId, request
        ));
    }

    @GetMapping("/vocabulary/mastery")
    public ResponseDto<VocabularyMasterySummaryResponseDto> vocabularyMastery(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseUtil.ok(masteryQueryService.get(
                SecurityUtil.getLoginUserId(principal)
        ));
    }
}
