package jp.co.translacat.domain.accountbook.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jp.co.translacat.domain.accountbook.dto.AccountBookCreateRequestDto;
import jp.co.translacat.domain.accountbook.dto.AccountBookResponseDto;
import jp.co.translacat.domain.accountbook.dto.AccountBookSearchRequestDto;
import jp.co.translacat.domain.accountbook.service.AccountBookService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/account-books")
@RequiredArgsConstructor
public class AccountBookController {

    private final AccountBookService accountBookService;

    @GetMapping
    @Operation(summary = "가계부 목록 조회", description = "특정 유저 또는 그룹에 등록되어 있는 가계부 목록을 조회한다.")
    public ResponseDto<List<AccountBookResponseDto>> list(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @ModelAttribute AccountBookSearchRequestDto searchDto
    ) {
        return ResponseUtil.ok(
                accountBookService.list(userPrincipal.getId(), searchDto)
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "가계부 등록", description = "특정 유저 또는 그룹에 가계부를 등록한다.")
    public ResponseDto<AccountBookResponseDto> register(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody AccountBookCreateRequestDto dto
    ) {
        return ResponseUtil.created(accountBookService.register(userPrincipal.getId(), dto));
    }



//    @GetMapping("/{accontBookId}")
//    @Operation(summary = "가계부 조회", description = "등록되어 있는 가계부 상세를 조회한다.")
//    public ResponseDto<>
}
