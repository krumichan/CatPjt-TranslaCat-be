package jp.co.translacat.domain.accountbook.receiptkeyword.repository;

import jp.co.translacat.domain.accountbook.receiptkeyword.entity.ReceiptKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptKeywordRepository extends
        JpaRepository<ReceiptKeyword, Long>,
        ReceiptKeywordRepositoryCustom {
}