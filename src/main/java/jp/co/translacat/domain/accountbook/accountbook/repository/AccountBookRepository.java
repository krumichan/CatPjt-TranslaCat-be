package jp.co.translacat.domain.accountbook.accountbook.repository;

import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountBookRepository extends JpaRepository<AccountBook, Long>, AccountBookRepositoryCustom {

    @EntityGraph(attributePaths = "currency")
    Optional<AccountBook> findByIdAndDeletedFalse(Long accountBookId);

    boolean existsByCurrency_Id(Long currencyId);
}
