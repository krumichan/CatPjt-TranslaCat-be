package jp.co.translacat.domain.accountbook.invitation.dto;

import jp.co.translacat.domain.accountbook.invitation.entity.AccountBookInvitation;
import jp.co.translacat.domain.accountbook.invitation.enums.AccountBookInvitationStatus;
import jp.co.translacat.domain.accountbook.member.enums.AccountBookMemberRole;

public record AccountBookInvitationResponseDto(
        Long id,
        Long accountBookId,
        String accountBookName,
        Long inviterUserId,
        String inviterPublicId,
        String inviterUsername,
        Long inviteeUserId,
        String inviteePublicId,
        String inviteeUsername,
        AccountBookMemberRole role,
        AccountBookInvitationStatus status
) {
    public static AccountBookInvitationResponseDto from(
            AccountBookInvitation invitation
    ) {
        return new AccountBookInvitationResponseDto(
                invitation.getId(),
                invitation.getAccountBook().getId(),
                invitation.getAccountBook().getName(),
                invitation.getInviter().getId(),
                invitation.getInviter().getPublicId(),
                invitation.getInviter().getUsername(),
                invitation.getInvitee().getId(),
                invitation.getInvitee().getPublicId(),
                invitation.getInvitee().getUsername(),
                invitation.getRole(),
                invitation.getStatus()
        );
    }
}