package jp.co.translacat.domain.languagelearning.daily.controller;

import jp.co.translacat.domain.languagelearning.daily.dto.request.AnswerSubmitRequestDto;
import jp.co.translacat.domain.languagelearning.daily.dto.response.AnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.daily.dto.response.DailyWritingSetResponseDto;
import jp.co.translacat.domain.languagelearning.daily.facade.DailyWritingFacade;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/language-learning/writing/daily")
@RequiredArgsConstructor
public class DailyWritingController {

    private final DailyWritingFacade dailyWritingFacade;

    @GetMapping
    public ResponseDto<DailyWritingSetResponseDto> getToday(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return ResponseUtil.ok(
                dailyWritingFacade.getOrGenerateToday(
                        SecurityUtil.getLoginUserId(userPrincipal)
                )
        );
    }

    @GetMapping("/history/{date}")
    public ResponseDto<DailyWritingSetResponseDto> getHistory(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable LocalDate date
    ) {
        return ResponseUtil.ok(
                dailyWritingFacade.getHistory(
                        SecurityUtil.getLoginUserId(userPrincipal),
                        date
                )
        );
    }

    @PostMapping("/{dailySetId}/regenerate")
    public ResponseDto<DailyWritingSetResponseDto> regenerate(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long dailySetId
    ) {
        return ResponseUtil.ok(
                dailyWritingFacade.regenerateUnanswered(
                        SecurityUtil.getLoginUserId(userPrincipal),
                        dailySetId
                )
        );
    }

    @PostMapping("/items/{itemId}/answers")
    public ResponseDto<AnswerResultResponseDto> submitAnswer(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long itemId,
            @RequestBody AnswerSubmitRequestDto request
    ) {
        return ResponseUtil.ok(
                dailyWritingFacade.submitAnswer(
                        SecurityUtil.getLoginUserId(userPrincipal),
                        itemId,
                        request
                )
        );
    }
}
