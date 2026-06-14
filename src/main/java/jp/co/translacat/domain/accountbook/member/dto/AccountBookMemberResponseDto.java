package jp.co.translacat.domain.accountbook.member.dto;

import jp.co.translacat.domain.accountbook.member.entity.AccountBookMember;
import jp.co.translacat.domain.accountbook.member.enums.AccountBookMemberRole;

public record AccountBookMemberResponseDto(
        Long id,
        Long userId,
        String publicId,
        String username,
        AccountBookMemberRole role
) {
    public static AccountBookMemberResponseDto from(AccountBookMember member) {
        return new AccountBookMemberResponseDto(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getPublicId(),
                member.getUser().getUsername(),
                member.getRole()
        );
    }
}
