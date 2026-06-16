package jp.co.translacat.domain.accountbook.invitation.repository;

import jp.co.translacat.domain.accountbook.invitation.entity.AccountBookInvitation;
import jp.co.translacat.domain.accountbook.invitation.enums.AccountBookInvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountBookInvitationRepository extends JpaRepository<AccountBookInvitation, Long> {

    boolean existsByAccountBook_IdAndInvitee_IdAndStatus(
            Long accountBookId,
            Long inviteeUserId,
            AccountBookInvitationStatus status
    );

    Optional<AccountBookInvitation> findByIdAndAccountBook_Id(
            Long invitationId,
            Long accountBookId
    );

    List<AccountBookInvitation> findByAccountBook_IdAndStatusOrderByIdDesc(
            Long accountBookId,
            AccountBookInvitationStatus status
    );

    List<AccountBookInvitation> findByInvitee_IdAndStatusOrderByIdDesc(
            Long inviteeUserId,
            AccountBookInvitationStatus status
    );

    Optional<AccountBookInvitation> findByIdAndInvitee_IdAndStatus(
            Long invitationId,
            Long inviteeUserId,
            AccountBookInvitationStatus status
    );
}