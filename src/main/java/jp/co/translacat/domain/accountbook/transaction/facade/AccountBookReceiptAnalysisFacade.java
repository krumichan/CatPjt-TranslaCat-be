package jp.co.translacat.domain.accountbook.transaction.facade;

import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.receiptkeyword.service.ReceiptAnalysisOptionQueryService;
import jp.co.translacat.domain.accountbook.transaction.dto.ReceiptAnalysisResponseDto;
import jp.co.translacat.domain.accountbook.transaction.enums.ReceiptAnalysisMode;
import jp.co.translacat.domain.accountbook.transaction.service.ReceiptConversionService;
import jp.co.translacat.domain.currency.entity.Currency;
import jp.co.translacat.infrastructure.client.ai.server.AiServerClient;
import jp.co.translacat.infrastructure.client.ai.server.dto.AiReceiptAnalysisResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountBookReceiptAnalysisFacade {
    private final AccountBookAccessService accountBookAccessService;
    private final ReceiptAnalysisOptionQueryService receiptAnalysisOptionQueryService;
    private final AiServerClient aiServerClient;
    private final ReceiptConversionService conversions;

    public ReceiptAnalysisResponseDto analyze(
            Long bookId, Long userId, MultipartFile file, String mode) {
        long started = System.nanoTime();
        var book = accountBookAccessService.getAccessibleAccountBook(bookId, userId);
        var options =
                receiptAnalysisOptionQueryService
                        .getOptions(bookId)
                        .withAnalysisMode(ReceiptAnalysisMode.fromNullable(mode).name());
        var response = aiServerClient.callReceiptAnalysis(file, options);
        if (response == null)
            throw new IllegalArgumentException("Receipt analysis returned no response.");
        List<ReceiptAnalysisResponseDto.Item> results = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (var item :
                response.receipts() == null
                        ? List.<AiReceiptAnalysisResponse.Item>of()
                        : response.receipts()) {
            if (results.size() == 30) break;
            String id = item == null ? null : item.receiptId();
            if (id == null || id.isBlank() || id.length() > 100 || !ids.add(id)) {
                id = "receipt-" + (results.size() + 1) + "-" + UUID.randomUUID();
                ids.add(id);
            }
            results.add(map(item, id, book.getCurrency(), options.categoryCandidates()));
        }
        long ready =
                results.stream()
                        .filter(
                                i ->
                                        "READY".equals(i.status())
                                                && ("CONVERTED".equals(i.conversionStatus())
                                                        || "NOT_REQUIRED"
                                                                .equals(i.conversionStatus())))
                        .count();
        log.info(
                "receipt_analysis accountBookId={} mode={} count={} ready={} review={}"
                    + " latencyMs={}",
                bookId,
                options.analysisMode(),
                results.size(),
                ready,
                results.size() - ready,
                (System.nanoTime() - started) / 1_000_000);
        List<String> topWarnings = new ArrayList<>(safe(response.warnings()));
        if (response.receipts() != null && response.receipts().size() > 30)
            topWarnings.add("RECEIPT_LIMIT_REACHED");
        return new ReceiptAnalysisResponseDto(
                results, results.size(), topWarnings, response.ocrEngine(), response.usedAi());
    }

    private ReceiptAnalysisResponseDto.Item map(
            AiReceiptAnalysisResponse.Item item,
            String id,
            Currency target,
            List<String> categories) {
        if (item == null)
            item =
                    new AiReceiptAnalysisResponse.Item(
                            id,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            "UNREADABLE",
                            List.of("UNREADABLE"));
        List<String> warnings = new ArrayList<>(safe(item.warnings()));
        BigDecimal amount = null;
        LocalDate date = null;
        try {
            if (item.originalAmount() != null && item.originalAmount().length() < 100)
                amount = new BigDecimal(item.originalAmount());
        } catch (NumberFormatException e) {
            warnings.add("INVALID_AMOUNT");
        }
        // Reject out-of-storage-range model values before plain-decimal serialization.
        // In particular an exponent such as 1E+50000000 must never expand into a response.
        if (amount != null
                && (amount.signum() <= 0
                        || amount.stripTrailingZeros().scale() > 8
                        || (long) amount.precision() - amount.scale() > 20)) {
            amount = null;
            warnings.add("INVALID_AMOUNT");
        }
        try {
            if (item.transactionDate() != null) date = LocalDate.parse(item.transactionDate());
        } catch (java.time.DateTimeException e) {
            warnings.add("INVALID_DATE");
        }
        var conversion = conversions.convert(amount, item.detectedCurrencyCode(), date, target);
        warnings.addAll(conversion.warnings());
        String category = item.categoryName();
        if (category == null || !categories.contains(category)) {
            category = null;
            warnings.add("CATEGORY_REQUIRES_REVIEW");
        }
        Double confidence = item.confidence();
        if (confidence != null
                && (!Double.isFinite(confidence) || confidence < 0 || confidence > 1)) {
            confidence = null;
            warnings.add("INVALID_CONFIDENCE");
        }
        String status =
                "UNREADABLE".equals(item.status())
                        ? "UNREADABLE"
                        : ("READY".equals(item.status())
                                        && conversion.registrable()
                                        && category != null
                                        && item.title() != null
                                        && !item.title().isBlank())
                                ? "READY"
                                : "NEEDS_REVIEW";
        return new ReceiptAnalysisResponseDto.Item(
                id,
                item.title(),
                item.storeName(),
                amount,
                conversion.originalCurrencyCode(),
                date,
                category,
                item.memo(),
                confidence,
                item.detectedLanguage(),
                status,
                warnings.stream().distinct().toList(),
                conversion.originalCurrencyCode(),
                target.getCode(),
                conversion.convertedAmount(),
                conversion.exchangeRate(),
                conversion.requestedRateDate(),
                conversion.effectiveRateDate(),
                conversion.exchangeRateProvider(),
                conversion.conversionStatus(),
                conversion.rateDateFallback());
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }
}
