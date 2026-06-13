package jp.co.translacat.domain.accountbook.accountbook.repository;

import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountBookRepository extends JpaRepository<AccountBook, Long>, AccountBookRepositoryCustom {

    Optional<AccountBook> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

    Optional<AccountBook> findByIdAndDeletedFalse(Long accountBookId);

    Optional<AccountBook> findByIdAndUser_IdAndDeletedFalse(Long id, Long userId);
}