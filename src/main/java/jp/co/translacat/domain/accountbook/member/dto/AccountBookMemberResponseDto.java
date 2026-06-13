package jp.co.translacat.domain.accountbook.member.dto;

import jp.co.translacat.domain.accountbook.member.entity.AccountBookMember;

public record AccountBookMemberResponseDto(
        Long id,
        Long userId,
        String email,
        String username,
        String role
) {
    public static AccountBookMemberResponseDto from(AccountBookMember member) {
        return new AccountBookMemberResponseDto(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getUser().getUsername(),
                member.getRole().name()
        );
    }
}