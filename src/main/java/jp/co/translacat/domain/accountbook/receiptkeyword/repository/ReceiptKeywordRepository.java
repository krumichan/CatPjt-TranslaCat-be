package jp.co.translacat.domain.accountbook.receiptkeyword.repository;

import jp.co.translacat.domain.accountbook.receiptkeyword.entity.ReceiptKeyword;
import jp.co.translacat.domain.accountbook.receiptkeyword.enums.ReceiptKeywordType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReceiptKeywordRepository extends
        JpaRepository<ReceiptKeyword, Long>,
        ReceiptKeywordRepositoryCustom {

    Optional<ReceiptKeyword> findByIdAndDeletedFalse(Long id);

    List<ReceiptKeyword> findAllByDeletedFalseOrderByOcrLanguageAscCurrencyCodeAscKeywordTypeAscDisplayOrderAscIdAsc();

    Optional<ReceiptKeyword> findFirstByCurrencyCodeAndOcrLanguageAndKeywordTypeAndKeywordAndDeletedFalse(
            String currencyCode,
            String ocrLanguage,
            ReceiptKeywordType keywordType,
            String keyword
    );
}