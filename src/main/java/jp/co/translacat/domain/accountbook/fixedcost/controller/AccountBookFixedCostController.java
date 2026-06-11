package jp.co.translacat.domain.accountbook.fixedcost.controller;

import jakarta.validation.Valid;
import jp.co.translacat.domain.accountbook.fixedcost.dto.AccountBookFixedCostActiveRequestDto;
import jp.co.translacat.domain.accountbook.fixedcost.dto.AccountBookFixedCostRequestDto;
import jp.co.translacat.domain.accountbook.fixedcost.dto.AccountBookFixedCostResponseDto;
import jp.co.translacat.domain.accountbook.fixedcost.service.AccountBookFixedCostService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/account-books/{accountBookId}/fixed-costs")
@RequiredArgsConstructor
public class AccountBookFixedCostController {

    private final AccountBookFixedCostService accountBookFixedCostService;

    @GetMapping
    public ResponseDto<List<AccountBookFixedCostResponseDto>> getFixedCosts(
            @PathVariable Long accountBookId
    ) {
        return ResponseUtil.ok(
                accountBookFixedCostService.getFixedCosts(accountBookId)
        );
    }

    @PostMapping
    public ResponseDto<AccountBookFixedCostResponseDto> createFixedCost(
            @PathVariable Long accountBookId,
            @Valid @RequestBody AccountBookFixedCostRequestDto request
    ) {
        return ResponseUtil.ok(
                accountBookFixedCostService.createFixedCost(accountBookId, request)
        );
    }

    @PutMapping("/{fixedCostId}")
    public ResponseDto<AccountBookFixedCostResponseDto> updateFixedCost(
            @PathVariable Long accountBookId,
            @PathVariable Long fixedCostId,
            @Valid @RequestBody AccountBookFixedCostRequestDto request
    ) {
        return ResponseUtil.ok(
                accountBookFixedCostService.updateFixedCost(
                        accountBookId,
                        fixedCostId,
                        request
                )
        );
    }

    @PatchMapping("/{fixedCostId}/active")
    public ResponseDto<AccountBookFixedCostResponseDto> updateActive(
            @PathVariable Long accountBookId,
            @PathVariable Long fixedCostId,
            @Valid @RequestBody AccountBookFixedCostActiveRequestDto request
    ) {
        return ResponseUtil.ok(
                accountBookFixedCostService.updateActive(
                        accountBookId,
                        fixedCostId,
                        request.active()
                )
        );
    }

    @DeleteMapping("/{fixedCostId}")
    public ResponseDto<Void> deleteFixedCost(
            @PathVariable Long accountBookId,
            @PathVariable Long fixedCostId
    ) {
        accountBookFixedCostService.deleteFixedCost(accountBookId, fixedCostId);
        return ResponseUtil.ok(null);
    }
}