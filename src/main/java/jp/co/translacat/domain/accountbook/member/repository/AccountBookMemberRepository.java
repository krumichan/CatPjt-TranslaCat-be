package jp.co.translacat.domain.accountbook.member.repository;

import jp.co.translacat.domain.accountbook.member.entity.AccountBookMember;
import jp.co.translacat.domain.accountbook.member.enums.AccountBookMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountBookMemberRepository extends JpaRepository<AccountBookMember, Long> {

    boolean existsByAccountBook_IdAndUser_IdAndDeletedFalse(
            Long accountBookId,
            Long userId
    );

    boolean existsByAccountBook_IdAndUser_IdAndRoleAndDeletedFalse(
            Long accountBookId,
            Long userId,
            AccountBookMemberRole role
    );

    Optional<AccountBookMember> findByAccountBook_IdAndUser_Id(
            Long accountBookId,
            Long userId
    );

    List<AccountBookMember> findByAccountBook_IdAndDeletedFalseOrderByRoleAscIdAsc(
            Long accountBookId
    );
}