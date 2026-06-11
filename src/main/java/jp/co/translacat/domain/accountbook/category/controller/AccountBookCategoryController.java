package jp.co.translacat.domain.accountbook.category.controller;

import jakarta.validation.Valid;
import jp.co.translacat.domain.accountbook.category.dto.AccountBookCategoryRequestDto;
import jp.co.translacat.domain.accountbook.category.dto.AccountBookCategoryResponseDto;
import jp.co.translacat.domain.accountbook.category.service.AccountBookCategoryService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/account-books/{accountBookId}/categories")
@RequiredArgsConstructor
public class AccountBookCategoryController {

    private final AccountBookCategoryService accountBookCategoryService;

    @GetMapping
    public ResponseDto<List<AccountBookCategoryResponseDto>> getCategories(
            @PathVariable Long accountBookId
    ) {
        return ResponseUtil.ok(
                accountBookCategoryService.getCategories(accountBookId)
        );
    }

    @PostMapping
    public ResponseDto<AccountBookCategoryResponseDto> createCategory(
            @PathVariable Long accountBookId,
            @Valid @RequestBody AccountBookCategoryRequestDto request
    ) {
        return ResponseUtil.ok(
                accountBookCategoryService.createCategory(accountBookId, request)
        );
    }
}