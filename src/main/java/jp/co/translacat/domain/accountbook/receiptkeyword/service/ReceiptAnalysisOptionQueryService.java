package jp.co.translacat.domain.accountbook.receiptkeyword.service;

import jp.co.translacat.domain.accountbook.receiptkeyword.entity.ReceiptKeyword;
import jp.co.translacat.domain.accountbook.receiptkeyword.entity.ReceiptOcrSetting;
import jp.co.translacat.domain.accountbook.receiptkeyword.enums.ReceiptKeywordType;
import jp.co.translacat.domain.accountbook.receiptkeyword.repository.ReceiptKeywordRepository;
import jp.co.translacat.domain.accountbook.receiptkeyword.repository.ReceiptOcrSettingRepository;
import jp.co.translacat.infrastructure.client.ai.server.dto.AiReceiptAnalysisOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceiptAnalysisOptionQueryService {

    private final ReceiptOcrSettingRepository receiptOcrSettingRepository;
    private final ReceiptKeywordRepository receiptKeywordRepository;

    public AiReceiptAnalysisOptions getOptions(String currencyCode) {
        String normalizedCurrencyCode = normalizeCurrencyCode(currencyCode);
        String ocrLanguage = getOcrLanguage(normalizedCurrencyCode);

        Map<ReceiptKeywordType, List<String>> keywordMap = getKeywordMap(
                normalizedCurrencyCode,
                ocrLanguage
        );

        return new AiReceiptAnalysisOptions(
                normalizedCurrencyCode,
                ocrLanguage,
                nullIfEmpty(keywordMap.get(ReceiptKeywordType.STOP_AFTER)),
                nullIfEmpty(keywordMap.get(ReceiptKeywordType.IMPORTANT)),
                nullIfEmpty(keywordMap.get(ReceiptKeywordType.EXCLUDE_ITEM))
        );
    }

    private String getOcrLanguage(String currencyCode) {
        return receiptOcrSettingRepository
                .findFirstByCurrencyCodeAndEnabledTrueAndDeletedFalse(currencyCode)
                .map(ReceiptOcrSetting::getOcrLanguage)
                .orElse("en");
    }

    private Map<ReceiptKeywordType, List<String>> getKeywordMap(
            String currencyCode,
            String ocrLanguage
    ) {
        List<ReceiptKeyword> keywords = receiptKeywordRepository.findEffectiveKeywords(
                currencyCode,
                ocrLanguage
        );

        Map<ReceiptKeywordType, LinkedHashSet<String>> grouped = new EnumMap<>(
                ReceiptKeywordType.class
        );

        for (ReceiptKeywordType type : ReceiptKeywordType.values()) {
            grouped.put(type, new LinkedHashSet<>());
        }

        for (ReceiptKeyword keyword : keywords) {
            grouped.get(keyword.getKeywordType()).add(keyword.getKeyword());
        }

        Map<ReceiptKeywordType, List<String>> result = new EnumMap<>(
                ReceiptKeywordType.class
        );

        for (Map.Entry<ReceiptKeywordType, LinkedHashSet<String>> entry : grouped.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        return result;
    }

    private String normalizeCurrencyCode(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return "JPY";
        }

        return currencyCode.trim().toUpperCase();
    }

    private List<String> nullIfEmpty(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        return values;
    }
}