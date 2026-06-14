package jp.co.translacat.domain.accountbook.receiptkeyword.repository;

import jp.co.translacat.domain.accountbook.receiptkeyword.entity.ReceiptKeyword;

import java.util.List;

public interface ReceiptKeywordRepositoryCustom {

    List<ReceiptKeyword> findEffectiveKeywords(
            String currencyCode,
            String ocrLanguage
    );
}