package jp.co.translacat.domain.accountbook.category.repository;

import jp.co.translacat.domain.accountbook.category.entity.AccountBookCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountBookCategoryRepository extends JpaRepository<AccountBookCategory, Long> {

    List<AccountBookCategory> findByAccountBookIdAndActiveTrueOrderByDisplayOrderAscNameAsc(
            Long accountBookId
    );

    Optional<AccountBookCategory> findByAccountBookIdAndName(
            Long accountBookId,
            String name
    );

    boolean existsByAccountBookIdAndName(
            Long accountBookId,
            String name
    );
}