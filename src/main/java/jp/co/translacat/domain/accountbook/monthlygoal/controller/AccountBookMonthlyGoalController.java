package jp.co.translacat.domain.accountbook.monthlygoal.controller;

import jakarta.validation.Valid;
import jp.co.translacat.domain.accountbook.monthlygoal.dto.AccountBookMonthlyGoalListItemResponseDto;
import jp.co.translacat.domain.accountbook.monthlygoal.dto.AccountBookMonthlyGoalRequestDto;
import jp.co.translacat.domain.accountbook.monthlygoal.dto.AccountBookMonthlyGoalResponseDto;
import jp.co.translacat.domain.accountbook.monthlygoal.facade.AccountBookMonthlyGoalFacade;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/account-books/{accountBookId}/monthly-goals")
@RequiredArgsConstructor
public class AccountBookMonthlyGoalController {

    private final AccountBookMonthlyGoalFacade accountBookMonthlyGoalFacade;

    @GetMapping
    public ResponseDto<AccountBookMonthlyGoalResponseDto> getMonthlyGoal(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId,
            @RequestParam Integer year,
            @RequestParam Integer month
    ) {
        return ResponseUtil.ok(
                accountBookMonthlyGoalFacade.getMonthlyGoal(
                        accountBookId,
                        year,
                        month,
                        userPrincipal.getId()

                )
        );
    }

    @GetMapping("/list")
    public ResponseDto<List<AccountBookMonthlyGoalListItemResponseDto>> getMonthlyGoalList(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId
    ) {
        return ResponseUtil.ok(
                accountBookMonthlyGoalFacade.getMonthlyGoalList(
                        accountBookId,
                        userPrincipal.getId())
        );
    }

    @PutMapping
    public ResponseDto<AccountBookMonthlyGoalResponseDto> saveMonthlyGoal(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId,
            @RequestBody @Valid AccountBookMonthlyGoalRequestDto request
    ) {
        return ResponseUtil.ok(
                accountBookMonthlyGoalFacade.saveMonthlyGoal(
                        accountBookId,
                        request,
                        userPrincipal.getId()
                )
        );
    }
}