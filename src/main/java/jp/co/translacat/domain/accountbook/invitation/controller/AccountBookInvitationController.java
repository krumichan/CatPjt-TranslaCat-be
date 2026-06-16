package jp.co.translacat.domain.accountbook.invitation.controller;

import jakarta.validation.Valid;
import jp.co.translacat.domain.accountbook.invitation.dto.AccountBookInvitationCreateRequestDto;
import jp.co.translacat.domain.accountbook.invitation.dto.AccountBookInvitationResponseDto;
import jp.co.translacat.domain.accountbook.invitation.service.AccountBookInvitationService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AccountBookInvitationController {

    private final AccountBookInvitationService accountBookInvitationService;

    @PostMapping("/account-books/{accountBookId}/invitations")
    public ResponseDto<AccountBookInvitationResponseDto> createInvitation(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId,
            @RequestBody @Valid AccountBookInvitationCreateRequestDto request
    ) {
        return ResponseUtil.ok(
                accountBookInvitationService.createInvitation(
                        accountBookId,
                        request,
                        userPrincipal.getId()
                )
        );
    }

    @DeleteMapping("/account-books/{accountBookId}/invitations/{invitationId}")
    public ResponseDto<Boolean> cancelInvitation(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId,
            @PathVariable Long invitationId
    ) {
        return ResponseUtil.ok(
                accountBookInvitationService.cancelInvitation(
                        accountBookId,
                        invitationId,
                        userPrincipal.getId()
                )
        );
    }

    @GetMapping("/account-books/{accountBookId}/invitations")
    public ResponseDto<List<AccountBookInvitationResponseDto>> getPendingInvitationsByAccountBook(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId
    ) {
        return ResponseUtil.ok(
                accountBookInvitationService.getPendingInvitationsByAccountBook(
                        accountBookId,
                        userPrincipal.getId()
                )
        );
    }

    @GetMapping("/account-book-invitations/received")
    public ResponseDto<List<AccountBookInvitationResponseDto>> getReceivedPendingInvitations(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return ResponseUtil.ok(
                accountBookInvitationService.getReceivedPendingInvitations(
                        userPrincipal.getId()
                )
        );
    }

    @PostMapping("/account-book-invitations/{invitationId}/accept")
    public ResponseDto<AccountBookInvitationResponseDto> acceptInvitation(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long invitationId
    ) {
        return ResponseUtil.ok(
                accountBookInvitationService.acceptInvitation(
                        invitationId,
                        userPrincipal.getId()
                )
        );
    }

    @PostMapping("/account-book-invitations/{invitationId}/reject")
    public ResponseDto<AccountBookInvitationResponseDto> rejectInvitation(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long invitationId
    ) {
        return ResponseUtil.ok(
                accountBookInvitationService.rejectInvitation(
                        invitationId,
                        userPrincipal.getId()
                )
        );
    }
}