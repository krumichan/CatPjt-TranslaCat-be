package jp.co.translacat.domain.accountbook.receiptkeyword.service;

import jp.co.translacat.domain.accountbook.category.entity.AccountBookCategory;
import jp.co.translacat.domain.accountbook.category.repository.AccountBookCategoryRepository;
import jp.co.translacat.domain.accountbook.receiptkeyword.entity.ReceiptKeyword;
import jp.co.translacat.domain.accountbook.receiptkeyword.enums.ReceiptKeywordType;
import jp.co.translacat.domain.accountbook.receiptkeyword.repository.ReceiptKeywordRepository;
import jp.co.translacat.infrastructure.client.ai.server.dto.AiReceiptAnalysisOptions;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceiptAnalysisOptionQueryService {
    private final ReceiptKeywordRepository receiptKeywordRepository;
    private final AccountBookCategoryRepository categoryRepository;

    public AiReceiptAnalysisOptions getOptions(Long accountBookId) {
        // Legacy currency-specific settings stay editable, never selected from target currency.
        var keywords =
                receiptKeywordRepository
                        .findByCurrencyCodeIsNullAndEnabledTrueAndDeletedFalseOrderByDisplayOrderAscIdAsc();
        return new AiReceiptAnalysisOptions(
                null,
                null,
                words(keywords, ReceiptKeywordType.STOP_AFTER),
                words(keywords, ReceiptKeywordType.IMPORTANT),
                words(keywords, ReceiptKeywordType.EXCLUDE_ITEM),
                categoryRepository
                        .findByAccountBookIdAndActiveTrueOrderByDisplayOrderAscNameAsc(
                                accountBookId)
                        .stream()
                        .map(AccountBookCategory::getName)
                        .toList(),
                ReceiptCategorySuggestionPolicy.DEFAULT_CATEGORIES);
    }

    private List<String> words(List<ReceiptKeyword> keywords, ReceiptKeywordType type) {
        return keywords.stream()
                .filter(k -> k.getKeywordType() == type)
                .map(ReceiptKeyword::getKeyword)
                .distinct()
                .toList();
    }
}
