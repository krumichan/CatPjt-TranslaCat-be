package jp.co.translacat.domain.accountbook.transaction.facade;

import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.receiptkeyword.service.ReceiptAnalysisOptionQueryService;
import jp.co.translacat.domain.accountbook.receiptkeyword.service.ReceiptCategorySuggestionPolicy;
import jp.co.translacat.domain.accountbook.transaction.dto.ReceiptAnalysisResponseDto;
import jp.co.translacat.domain.accountbook.transaction.dto.ReceiptPaymentItemDto;
import jp.co.translacat.domain.accountbook.transaction.dto.ReceiptRuntimeIdentityResponseDto;
import jp.co.translacat.domain.accountbook.transaction.enums.ReceiptAnalysisMode;
import jp.co.translacat.domain.accountbook.transaction.service.ReceiptConversionService;
import jp.co.translacat.domain.accountbook.transaction.service.ReceiptAmountPolicy;
import jp.co.translacat.domain.currency.entity.Currency;
import jp.co.translacat.infrastructure.client.ai.server.AiServerClient;
import jp.co.translacat.infrastructure.client.ai.server.dto.AiReceiptAnalysisResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
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
        boolean categoryCandidatesDelivered = options.categoryCandidates() != null;
        List<String> categoryCandidates = categoryCandidatesDelivered
                ? options.categoryCandidates()
                : List.of();
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
            results.add(map(item, id, book.getCurrency(), categoryCandidates,
                    options.defaultCategoryCandidates(), bookId));
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
        if (!categoryCandidatesDelivered) topWarnings.add("CATEGORY_CANDIDATES_NOT_DELIVERED");
        else if (categoryCandidates.isEmpty()) topWarnings.add("CATEGORY_CANDIDATES_EMPTY");
        if (response.receipts() != null && response.receipts().size() > 30)
            topWarnings.add("RECEIPT_LIMIT_REACHED");
        List<ReceiptAnalysisResponseDto.CategoryOption> categoryOptions = new ArrayList<>();
        categoryCandidates.forEach(name -> categoryOptions.add(
                new ReceiptAnalysisResponseDto.CategoryOption(name, "EXISTING")));
        options.defaultCategoryCandidates().stream()
                .filter(name -> categoryCandidates.stream().noneMatch(name::equalsIgnoreCase))
                .forEach(name -> categoryOptions.add(
                        new ReceiptAnalysisResponseDto.CategoryOption(name, "DEFAULT")));
        return new ReceiptAnalysisResponseDto(
                results, results.size(), topWarnings, response.ocrEngine(), response.usedAi(),
                categoryOptions, response.analysisTraceId(),
                ReceiptRuntimeIdentityResponseDto.from(response.runtimeIdentity()));
    }

    public ReceiptRuntimeIdentityResponseDto runtimeIdentity(Long bookId, Long userId) {
        accountBookAccessService.getAccessibleAccountBook(bookId, userId);
        return ReceiptRuntimeIdentityResponseDto.from(aiServerClient.callReceiptRuntimeIdentity());
    }

    private ReceiptAnalysisResponseDto.Item map(
            AiReceiptAnalysisResponse.Item item,
            String id,
            Currency target,
            List<String> categories,
            List<String> defaultCategories,
            Long bookId) {
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
        BigDecimal purchaseTotal = parseMoney(
                item.purchaseTotal() != null ? item.purchaseTotal() : item.originalAmount(),
                false, warnings, "INVALID_AMOUNT");
        BigDecimal cashTendered = parseMoney(
                item.cashTendered(), true, warnings, "INVALID_CASH_TENDERED");
        BigDecimal change = parseMoney(item.change(), true, warnings, "INVALID_CHANGE");
        List<ReceiptPaymentItemDto> payments = new ArrayList<>();
        for (var payment : item.paymentBreakdown() == null
                ? List.<AiReceiptAnalysisResponse.PaymentItem>of()
                : item.paymentBreakdown()) {
            BigDecimal paymentAmount = payment == null ? null : parseMoney(
                    payment.amount(), false, warnings, "INVALID_PAYMENT_AMOUNT");
            payments.add(payment == null ? null : new ReceiptPaymentItemDto(
                    payment.paymentType(), paymentAmount, payment.evidence(),
                    payment.duplicateGroup()));
        }
        var amountDecision = ReceiptAmountPolicy.decide(
                purchaseTotal, payments, cashTendered, change);
        warnings.addAll(amountDecision.warnings());
        BigDecimal amount = amountDecision.ready() ? amountDecision.bookAmount() : null;
        BigDecimal aiBookAmount = parseMoney(
                item.bookAmount(), true, warnings, "INVALID_AI_BOOK_AMOUNT");
        if (aiBookAmount != null && amountDecision.bookAmount() != null
                && aiBookAmount.compareTo(amountDecision.bookAmount()) != 0)
            warnings.add("AI_BOOK_AMOUNT_DISAGREED");
        LocalDate date = null;
        try {
            if (item.transactionDate() != null) date = LocalDate.parse(item.transactionDate());
        } catch (java.time.DateTimeException e) {
            warnings.add("INVALID_DATE");
        }
        String transactionTime = null;
        try {
            if (item.transactionTime() != null)
                transactionTime = LocalTime.parse(item.transactionTime()).toString();
        } catch (java.time.DateTimeException e) {
            warnings.add("INVALID_TIME");
        }
        var conversion = conversions.convert(
                amount, item.detectedCurrencyCode(), date, target, bookId,
                amountDecision.fingerprint());
        warnings.addAll(conversion.warnings());
        var category = ReceiptCategorySuggestionPolicy.resolve(
                item.categoryName(), item.categoryReason(), categories, defaultCategories);
        warnings.addAll(category.warnings());
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
                                        && amountDecision.ready()
                                        && category.name() != null
                                        && item.title() != null
                                        && !item.title().isBlank())
                                ? "READY"
                                : "NEEDS_REVIEW";
        return new ReceiptAnalysisResponseDto.Item(
                id,
                item.title(),
                item.storeName(),
                item.branchName(),
                item.merchantEvidence(),
                item.branchEvidence(),
                item.boundingBox(),
                item.identitySourceBox(),
                item.identityVerification(),
                item.financialSourceBox(),
                item.financialRecoveryProvenance(),
                amountDecision.purchaseTotal(),
                amountDecision.payments(),
                amountDecision.cashTendered(),
                amountDecision.change(),
                amountDecision.bookAmount(),
                amountDecision.policyVersion(),
                amountDecision.reason(),
                amountDecision.status(),
                amountDecision.bookAmount(),
                conversion.originalCurrencyCode(),
                date,
                transactionTime,
                category.name(),
                category.source(),
                category.reason(),
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
                conversion.rateFetchedAt(),
                conversion.convertedAt(),
                conversion.roundingPrecision(),
                conversion.roundingMode(),
                conversion.conversionPolicyVersion(),
                conversion.conversionQuoteId(),
                conversion.conversionStatus(),
                conversion.rateDateFallback());
    }

    private BigDecimal parseMoney(
            String raw, boolean allowZero, List<String> warnings, String warning) {
        if (raw == null) return null;
        BigDecimal value;
        try {
            if (raw.length() >= 100) throw new NumberFormatException("too long");
            value = new BigDecimal(raw);
        } catch (NumberFormatException e) {
            warnings.add(warning);
            return null;
        }
        if ((allowZero ? value.signum() < 0 : value.signum() <= 0)
                || value.stripTrailingZeros().scale() > 8
                || (long) value.precision() - value.scale() > 20) {
            warnings.add(warning);
            return null;
        }
        return value;
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }
}
