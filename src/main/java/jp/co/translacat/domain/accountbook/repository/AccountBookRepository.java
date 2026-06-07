package jp.co.translacat.domain.accountbook.repository;

import jp.co.translacat.domain.accountbook.entity.AccountBook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountBookRepository extends JpaRepository<AccountBook, Long>, AccountBookRepositoryCustom {

    List<AccountBook> findAllByUserIdAndDeletedFalseOrderByCreatedAtDesc(Long userId);

    Optional<AccountBook> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);
}