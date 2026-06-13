package jp.co.translacat.domain.accountbook.fixedcost.controller;

import jakarta.validation.Valid;
import jp.co.translacat.domain.accountbook.fixedcost.dto.*;
import jp.co.translacat.domain.accountbook.fixedcost.service.AccountBookFixedCostService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/account-books/{accountBookId}/fixed-costs")
@RequiredArgsConstructor
public class AccountBookFixedCostController {

    private final AccountBookFixedCostService accountBookFixedCostService;

    @GetMapping
    public ResponseDto<List<AccountBookFixedCostResponseDto>> getFixedCosts(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId
    ) {
        return ResponseUtil.ok(
                accountBookFixedCostService.getFixedCosts(
                        accountBookId,
                        userPrincipal.getId()
                )
        );
    }

    @PostMapping
    public ResponseDto<AccountBookFixedCostResponseDto> createFixedCost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId,
            @Valid @RequestBody AccountBookFixedCostRequestDto request
    ) {
        return ResponseUtil.ok(
                accountBookFixedCostService.createFixedCost(
                        accountBookId,
                        request,
                        userPrincipal.getId()
                )
        );
    }

    @PutMapping("/{fixedCostId}")
    public ResponseDto<AccountBookFixedCostResponseDto> updateFixedCost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId,
            @PathVariable Long fixedCostId,
            @Valid @RequestBody AccountBookFixedCostRequestDto request
    ) {
        return ResponseUtil.ok(
                accountBookFixedCostService.updateFixedCost(
                        accountBookId,
                        fixedCostId,
                        request,
                        userPrincipal.getId()
                )
        );
    }

    @PatchMapping("/{fixedCostId}/active")
    public ResponseDto<AccountBookFixedCostResponseDto> updateActive(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId,
            @PathVariable Long fixedCostId,
            @Valid @RequestBody AccountBookFixedCostActiveRequestDto request
    ) {
        return ResponseUtil.ok(
                accountBookFixedCostService.updateActive(
                        accountBookId,
                        fixedCostId,
                        request.active(),
                        userPrincipal.getId()
                )
        );
    }

    @DeleteMapping("/{fixedCostId}")
    public ResponseDto<Boolean> deleteFixedCost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId,
            @PathVariable Long fixedCostId
    ) {
        return ResponseUtil.ok(
                accountBookFixedCostService.deleteFixedCost(
                        accountBookId,
                        fixedCostId,
                        userPrincipal.getId()
                )
        );
    }

    @GetMapping("/generation-targets")
    public ResponseDto<AccountBookFixedCostGenerationTargetsResponseDto> getGenerationTargets(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId,
            @RequestParam Integer year,
            @RequestParam Integer month
    ) {
        return ResponseUtil.ok(
                accountBookFixedCostService.getGenerationTargets(
                        accountBookId,
                        year,
                        month,
                        userPrincipal.getId()
                )
        );
    }

    @PostMapping("/generate-transactions")
    public ResponseDto<AccountBookFixedCostGenerateResponseDto> generateTransactions(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId,
            @Valid @RequestBody AccountBookFixedCostGenerateRequestDto request
    ) {
        return ResponseUtil.ok(
                accountBookFixedCostService.generateTransactions(
                        accountBookId,
                        request,
                        userPrincipal.getId()
                )
        );
    }
}