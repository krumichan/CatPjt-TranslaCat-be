package jp.co.translacat.domain.accountbook.member.controller;

import jakarta.validation.Valid;
import jp.co.translacat.domain.accountbook.member.dto.AccountBookMemberInviteRequestDto;
import jp.co.translacat.domain.accountbook.member.dto.AccountBookMemberResponseDto;
import jp.co.translacat.domain.accountbook.member.service.AccountBookMemberService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/account-books/{accountBookId}/members")
@RequiredArgsConstructor
public class AccountBookMemberController {

    private final AccountBookMemberService accountBookMemberService;

    @GetMapping
    public ResponseDto<List<AccountBookMemberResponseDto>> getMembers(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId
    ) {
        return ResponseUtil.ok(
                accountBookMemberService.getMembers(
                        accountBookId,
                        userPrincipal.getId()
                )
        );
    }

    // TODO: 초대 승락/거절 모드 생기면서 제거.
//    @PostMapping
//    public ResponseDto<AccountBookMemberResponseDto> inviteMember(
//            @AuthenticationPrincipal UserPrincipal userPrincipal,
//            @PathVariable Long accountBookId,
//            @RequestBody @Valid AccountBookMemberInviteRequestDto request
//    ) {
//        return ResponseUtil.ok(
//                accountBookMemberService.inviteMember(
//                        accountBookId,
//                        request,
//                        userPrincipal.getId()
//                )
//        );
//    }

    @DeleteMapping("/{targetUserId}")
    public ResponseDto<Boolean> removeMember(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId,
            @PathVariable Long targetUserId
    ) {
        return ResponseUtil.ok(
                accountBookMemberService.removeMember(
                        accountBookId,
                        targetUserId,
                        userPrincipal.getId()
                )
        );
    }
}