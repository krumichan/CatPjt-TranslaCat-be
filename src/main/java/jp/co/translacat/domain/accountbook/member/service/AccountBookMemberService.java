package jp.co.translacat.domain.accountbook.member.service;

import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.member.dto.AccountBookMemberInviteRequestDto;
import jp.co.translacat.domain.accountbook.member.dto.AccountBookMemberResponseDto;
import jp.co.translacat.domain.accountbook.member.entity.AccountBookMember;
import jp.co.translacat.domain.accountbook.member.repository.AccountBookMemberRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountBookMemberService {

    private final AccountBookAccessService accountBookAccessService;
    private final AccountBookMemberRepository accountBookMemberRepository;
    private final UserService userService;

    public List<AccountBookMemberResponseDto> getMembers(
            Long accountBookId,
            Long userId
    ) {
        accountBookAccessService.getAccessibleAccountBook(
                accountBookId,
                userId
        );

        return accountBookMemberRepository
                .findByAccountBook_IdAndDeletedFalseOrderByRoleAscIdAsc(accountBookId)
                .stream()
                .map(AccountBookMemberResponseDto::from)
                .toList();
    }

    @Transactional
    public AccountBookMemberResponseDto inviteMember(
            Long accountBookId,
            AccountBookMemberInviteRequestDto request,
            Long ownerUserId
    ) {
        AccountBook accountBook = accountBookAccessService
                .getOwnerAccountBook(accountBookId, ownerUserId);

        User invitedUser = userService.findByPublicId(request.publicId());

        if (invitedUser == null) {
            throw new IllegalArgumentException("초대할 사용자를 찾을 수 없습니다.");
        }

        if (invitedUser.getId().equals(ownerUserId)) {
            throw new IllegalArgumentException("자기 자신은 초대할 수 없습니다.");
        }

        Optional<AccountBookMember> existingMember =
                accountBookMemberRepository.findByAccountBook_IdAndUser_Id(
                        accountBookId,
                        invitedUser.getId()
                );

        if (existingMember.isPresent()) {
            AccountBookMember member = existingMember.get();

            if (!member.isDeleted()) {
                throw new IllegalArgumentException("이미 가계부 멤버입니다.");
            }

            member.restoreAsMember();

            return AccountBookMemberResponseDto.from(member);
        }

        AccountBookMember member = AccountBookMember.createMember(
                accountBook,
                invitedUser
        );

        return AccountBookMemberResponseDto.from(
                accountBookMemberRepository.save(member)
        );
    }

    @Transactional
    public boolean removeMember(
            Long accountBookId,
            Long targetUserId,
            Long ownerUserId
    ) {
        accountBookAccessService.getOwnerAccountBook(
                accountBookId,
                ownerUserId
        );

        AccountBookMember member = accountBookMemberRepository
                .findByAccountBook_IdAndUser_Id(accountBookId, targetUserId)
                .filter(accountBookMember -> !accountBookMember.isOwner())
                .filter(accountBookMember -> !accountBookMember.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다."));

        member.delete();

        return true;
    }
}