package jp.co.translacat.domain.accountbook.invitation.service;

import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.invitation.dto.AccountBookInvitationCreateRequestDto;
import jp.co.translacat.domain.accountbook.invitation.dto.AccountBookInvitationResponseDto;
import jp.co.translacat.domain.accountbook.invitation.entity.AccountBookInvitation;
import jp.co.translacat.domain.accountbook.invitation.enums.AccountBookInvitationStatus;
import jp.co.translacat.domain.accountbook.invitation.repository.AccountBookInvitationRepository;
import jp.co.translacat.domain.accountbook.member.entity.AccountBookMember;
import jp.co.translacat.domain.accountbook.member.enums.AccountBookMemberRole;
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
public class AccountBookInvitationService {

    private final AccountBookAccessService accountBookAccessService;
    private final AccountBookInvitationRepository accountBookInvitationRepository;
    private final AccountBookMemberRepository accountBookMemberRepository;
    private final UserService userService;

    @Transactional
    public AccountBookInvitationResponseDto createInvitation(
            Long accountBookId,
            AccountBookInvitationCreateRequestDto request,
            Long inviterUserId
    ) {
        AccountBook accountBook = accountBookAccessService.getOwnerAccountBook(
                accountBookId,
                inviterUserId
        );

        User inviter = userService.getById(inviterUserId);

        User invitee = userService.findByPublicId(request.publicId());

        if (invitee == null) {
            throw new IllegalArgumentException("초대할 사용자를 찾을 수 없습니다.");
        }

        if (invitee.getId().equals(inviterUserId)) {
            throw new IllegalArgumentException("자기 자신은 초대할 수 없습니다.");
        }

        boolean alreadyMember = accountBookMemberRepository
                .existsByAccountBook_IdAndUser_IdAndDeletedFalse(
                        accountBookId,
                        invitee.getId()
                );

        if (alreadyMember) {
            throw new IllegalArgumentException("이미 가계부 멤버입니다.");
        }

        boolean alreadyPending = accountBookInvitationRepository
                .existsByAccountBook_IdAndInvitee_IdAndStatus(
                        accountBookId,
                        invitee.getId(),
                        AccountBookInvitationStatus.PENDING
                );

        if (alreadyPending) {
            throw new IllegalArgumentException("이미 대기 중인 초대가 있습니다.");
        }

        AccountBookInvitation invitation = AccountBookInvitation.createPending(
                accountBook,
                inviter,
                invitee,
                AccountBookMemberRole.MEMBER
        );

        return AccountBookInvitationResponseDto.from(
                accountBookInvitationRepository.save(invitation)
        );
    }

    @Transactional
    public Boolean cancelInvitation(
            Long accountBookId,
            Long invitationId,
            Long ownerUserId
    ) {
        accountBookAccessService.getOwnerAccountBook(
                accountBookId,
                ownerUserId
        );

        AccountBookInvitation invitation = accountBookInvitationRepository
                .findByIdAndAccountBook_Id(invitationId, accountBookId)
                .orElseThrow(() -> new IllegalArgumentException("초대를 찾을 수 없습니다."));

        invitation.cancel();

        return true;
    }

    public List<AccountBookInvitationResponseDto> getPendingInvitationsByAccountBook(
            Long accountBookId,
            Long ownerUserId
    ) {
        accountBookAccessService.getOwnerAccountBook(
                accountBookId,
                ownerUserId
        );

        return accountBookInvitationRepository
                .findByAccountBook_IdAndStatusOrderByIdDesc(
                        accountBookId,
                        AccountBookInvitationStatus.PENDING
                )
                .stream()
                .map(AccountBookInvitationResponseDto::from)
                .toList();
    }

    public List<AccountBookInvitationResponseDto> getReceivedPendingInvitations(
            Long inviteeUserId
    ) {
        return accountBookInvitationRepository
                .findByInvitee_IdAndStatusOrderByIdDesc(
                        inviteeUserId,
                        AccountBookInvitationStatus.PENDING
                )
                .stream()
                .map(AccountBookInvitationResponseDto::from)
                .toList();
    }

    @Transactional
    public AccountBookInvitationResponseDto acceptInvitation(
            Long invitationId,
            Long inviteeUserId
    ) {
        AccountBookInvitation invitation = accountBookInvitationRepository
                .findByIdAndInvitee_IdAndStatus(
                        invitationId,
                        inviteeUserId,
                        AccountBookInvitationStatus.PENDING
                )
                .orElseThrow(() -> new IllegalArgumentException("수락 가능한 초대를 찾을 수 없습니다."));

        Optional<AccountBookMember> existingMember =
                accountBookMemberRepository.findByAccountBook_IdAndUser_Id(
                        invitation.getAccountBook().getId(),
                        inviteeUserId
                );

        if (existingMember.isPresent()) {
            AccountBookMember member = existingMember.get();

            if (!member.isDeleted()) {
                throw new IllegalArgumentException("이미 가계부 멤버입니다.");
            }

            member.restoreAsRole(invitation.getRole());
        } else {
            accountBookMemberRepository.save(
                    AccountBookMember.createMember(
                            invitation.getAccountBook(),
                            invitation.getInvitee(),
                            invitation.getRole()
                    )
            );
        }

        invitation.accept();

        return AccountBookInvitationResponseDto.from(invitation);
    }

    @Transactional
    public AccountBookInvitationResponseDto rejectInvitation(
            Long invitationId,
            Long inviteeUserId
    ) {
        AccountBookInvitation invitation = accountBookInvitationRepository
                .findByIdAndInvitee_IdAndStatus(
                        invitationId,
                        inviteeUserId,
                        AccountBookInvitationStatus.PENDING
                )
                .orElseThrow(() -> new IllegalArgumentException("거절 가능한 초대를 찾을 수 없습니다."));

        invitation.reject();

        return AccountBookInvitationResponseDto.from(invitation);
    }
}