package jp.co.translacat.domain.accountbook.transaction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookSearchRequestDto;
import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.accountbook.repository.AccountBookRepository;
import jp.co.translacat.domain.accountbook.accountbook.repository.AccountBookSummaryRepositoryImpl;
import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.category.service.AccountBookCategoryService;
import jp.co.translacat.domain.accountbook.fixedcost.entity.AccountBookFixedCost;
import jp.co.translacat.domain.accountbook.member.entity.AccountBookMember;
import jp.co.translacat.domain.accountbook.monthlygoal.entity.AccountBookMonthlyGoal;
import jp.co.translacat.domain.accountbook.transaction.dto.*;
import jp.co.translacat.domain.accountbook.transaction.entity.AccountBookTransaction;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;
import jp.co.translacat.domain.accountbook.transaction.repository.AccountBookTransactionRepository;
import jp.co.translacat.domain.accountbook.transaction.repository.ReceiptBatchRegistrationRepository;
import jp.co.translacat.domain.currency.entity.Currency;
import jp.co.translacat.domain.currency.entity.ExchangeRate;
import jp.co.translacat.domain.currency.repository.ExchangeRateRepository;
import jp.co.translacat.domain.currency.service.ExchangeRateCache;
import jp.co.translacat.domain.currency.service.ExchangeRateProvider;
import jp.co.translacat.domain.currency.service.ExchangeRateService;
import jp.co.translacat.domain.currency.service.RateUnavailableException;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.config.QueryDslConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:receipt-persistence;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({
        QueryDslConfig.class,
        AccountBookAccessService.class,
        AccountBookCategoryService.class,
        ReceiptBatchService.class,
        ReceiptConversionService.class,
        ExchangeRateService.class,
        ExchangeRateCache.class,
        ReceiptBatchRegistrationExecutor.class,
        ReceiptBatchFingerprint.class,
        AccountBookTransactionService.class,
        AccountBookSummaryRepositoryImpl.class,
        ReceiptPersistenceIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReceiptPersistenceIntegrationTest {
    @TestConfiguration
    static class Config {
        @Bean
        ExchangeRateProvider fakeProvider() {
            return new ExchangeRateProvider() {
                public String name() {
                    return "TEST";
                }

                public Quote fetch(String source, String target, LocalDate requested) {
                    if (source.equals("EUR")) throw new RateUnavailableException();
                    return new Quote(new BigDecimal(source.equals("THB") ? "4.125" : "150.123456789012345678"),
                            requested.minusDays(1));
                }
            };
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }

    @Autowired
    EntityManager em;
    @Autowired
    PlatformTransactionManager manager;
    @Autowired
    ReceiptBatchService batch;
    @Autowired
    AccountBookTransactionService manual;
    @Autowired
    AccountBookTransactionRepository transactions;
    @Autowired
    ExchangeRateRepository rates;
    @Autowired
    ExchangeRateCache cache;
    @Autowired
    ReceiptBatchRegistrationRepository registrations;
    @Autowired
    AccountBookRepository books;
    @Autowired
    AccountBookSummaryRepositoryImpl summary;
    TransactionTemplate tx;
    Long bookId, userId;
    final LocalDate date = LocalDate.of(2026, 9, 12);

    @BeforeEach
    void fixture() {
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> {
            var user = User.createLocalUser("receipt-" + UUID.randomUUID() + "@test.local", "password", "receipt",
                    Role.USER, UUID.randomUUID().toString().substring(0, 10));
            em.persist(user);
            var currency = Currency.create("JPY", "Japanese Yen", "Y", 0, false);
            em.persist(currency);
            var book = AccountBook.create(user, currency, "Receipts", "Test");
            em.persist(book);
            em.persist(AccountBookMember.createOwner(book, user));
            em.flush();
            bookId = book.getId();
            userId = user.getId();
        });
    }

    @AfterEach
    void cleanup() {
        tx.executeWithoutResult(status -> {
            for (String name : List.of("ReceiptBatchRegistration", "AccountBookTransaction", "AccountBookFixedCost",
                    "AccountBookMonthlyGoal", "AccountBookCategory",
                    "AccountBookMember", "AccountBook", "ExchangeRate", "Currency", "User"))
                em.createQuery("delete from " + name).executeUpdate();
        });
    }

    ReceiptCandidateRequestDto item(String id, String source, String amount) {
        var conversion = batch.recalculate(
                bookId, userId, new ReceiptConversionRequestDto(new BigDecimal(amount), source, date));
        return new ReceiptCandidateRequestDto(
                id, "Purchase", "Store", "Food", new BigDecimal(amount), source, date, "Memo",
                conversion.conversionQuoteId());
    }

    ReceiptCandidateRequestDto itemWithCategory(String id, String categoryName) {
        var base = item(id, "JPY", "10");
        return new ReceiptCandidateRequestDto(
                base.receiptId(), base.title(), base.storeName(), base.branchName(), categoryName,
                "NEW", "영수증 품목에 맞는 신규 제안", base.purchaseTotal(), base.paymentBreakdown(),
                base.cashTendered(), base.change(), base.originalAmount(), base.originalCurrencyCode(),
                base.transactionDate(), base.transactionTime(), base.memo(), base.conversionQuoteId(),
                base.sourceImageId(), base.analysisRevision(), base.amountPolicyVersion(),
                base.amountReason(), base.reviewStatus(), null, null, null, null, null);
    }

    ReceiptCandidateRequestDto assisted(
            ReceiptCandidateRequestDto base, int draftRevision, int reviewedRevision) {
        return new ReceiptCandidateRequestDto(
                base.receiptId(), base.title(), base.storeName(), base.branchName(), base.categoryName(),
                base.categorySource(), base.categoryReason(), base.purchaseTotal(), base.paymentBreakdown(),
                base.cashTendered(), base.change(), base.originalAmount(), base.originalCurrencyCode(),
                base.transactionDate(), base.transactionTime(), base.memo(), base.conversionQuoteId(),
                base.sourceImageId(), base.analysisRevision(), base.amountPolicyVersion(),
                base.amountReason(), base.reviewStatus(), "ASSISTED", draftRevision,
                reviewedRevision, List.of(0.1, 0.1, 0.9, 0.9), false);
    }

    String key() {
        return "receipt-" + UUID.randomUUID();
    }

    @Test
    void batchPersistsOriginalFactsAndRateAndQueryProjection() {
        var result = batch.register(bookId, userId, key(),
                new ReceiptBatchRequestDto(List.of(item("1", "USD", "12.34"), item("2", "THB", "10.125"))));
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().amount()).isEqualByComparingTo("1853");
        assertThat(result.getFirst().originalAmount()).isEqualByComparingTo("12.34");
        assertThat(result.getFirst().originalCurrencyCode()).isEqualTo("USD");
        assertThat(result.getFirst().exchangeRate()).isEqualByComparingTo("150.123456789012345678");
        assertThat(result.getFirst().effectiveRateDate()).isEqualTo(date.minusDays(1));
        assertThat(result.getFirst().targetCurrencyCode()).isEqualTo("JPY");
        assertThat(result.getFirst().rateFetchedAt()).isNotNull();
        assertThat(result.getFirst().convertedAt()).isNotNull();
        assertThat(result.getFirst().roundingPrecision()).isZero();
        assertThat(result.getFirst().roundingMode()).isEqualTo("HALF_UP");
        assertThat(result.getFirst().conversionPolicyVersion()).isEqualTo("receipt-fx-v1");
        tx.executeWithoutResult(status -> {
            em.clear();
            var persisted = transactions.findById(result.getFirst().id()).orElseThrow();
            assertThat(persisted.getOriginalAmount()).isEqualByComparingTo("12.34");
            assertThat(persisted.getExchangeRate()).isEqualByComparingTo("150.123456789012345678");
            var page = transactions.findAllWithPage(bookId, new AccountBookTransactionRequestDto());
            assertThat(page.getContent()).hasSize(2).allSatisfy(row -> assertThat(row.originalAmount()).isNotNull());
            assertThat(em.createQuery("select count(c) from Currency c", Long.class).getSingleResult()).isEqualTo(1);
        });
    }

    @Test
    void papasuFactsPersistWith5020BookAmountAndSourceProvenance() {
        var payments = List.of(
                new ReceiptPaymentItemDto("LOYALTY_POINTS", new BigDecimal("2069"), "ポイント支払", null),
                new ReceiptPaymentItemDto("CREDIT_CARD", new BigDecimal("5020"), "クレジット", "card-1"),
                new ReceiptPaymentItemDto("CREDIT_CARD", new BigDecimal("5020"), "カード明細", "card-1"));
        var preview = batch.recalculate(bookId, userId, new ReceiptConversionRequestDto(
                new BigDecimal("5020"), "JPY", date, new BigDecimal("7089"),
                payments, null, null));
        var candidate = new ReceiptCandidateRequestDto(
                "papasu", "どらっぐ ぱぱす 船堀店", "どらっぐ ぱぱす", "船堀店", "Food",
                new BigDecimal("7089"), payments, null, null, new BigDecimal("5020"),
                "JPY", date, "14:23", "買い物", preview.conversionQuoteId(), "image-papasu", 1,
                ReceiptAmountPolicy.VERSION, "SETTLED_PAYMENT_EXCLUDING_LOYALTY_POINTS", "READY");
        var created = batch.register(bookId, userId, key(),
                new ReceiptBatchRequestDto(List.of(candidate))).getFirst();
        assertThat(created.amount()).isEqualByComparingTo("5020");
        assertThat(created.originalAmount()).isEqualByComparingTo("5020");
        tx.executeWithoutResult(status -> {
            em.clear();
            var persisted = transactions.findById(created.id()).orElseThrow();
            assertThat(persisted.getPurchaseTotal()).isEqualByComparingTo("7089");
            assertThat(persisted.getBookAmount()).isEqualByComparingTo("5020");
            assertThat(persisted.getReceiptPaymentBreakdownJson())
                    .contains("LOYALTY_POINTS", "2069", "CREDIT_CARD", "5020");
            assertThat(persisted.getReceiptBranchName()).isEqualTo("船堀店");
            assertThat(persisted.getMerchantKey()).isEqualTo("どらっぐ ぱぱす");
            assertThat(persisted.getReceiptSourceImageId()).isEqualTo("image-papasu");
            assertThat(persisted.getReceiptAnalysisRevision()).isEqualTo(1);
            assertThat(persisted.getReceiptTransactionTime()).isEqualTo("14:23");
        });
    }

    @Test
    void failedSecondReceiptRollsBackFirstTransactionAndNewCategory() {
        assertThatThrownBy(() -> batch.register(bookId, userId, key(),
                new ReceiptBatchRequestDto(List.of(item("1", "USD", "12.34"), item("2", "EUR", "10")))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("RATE_UNAVAILABLE");
        assertThat(transactions.count()).isZero();
        tx.executeWithoutResult(status -> assertThat(
                em.createQuery("select count(c) from AccountBookCategory c", Long.class).getSingleResult()).isZero());
        // Published daily cache entries are intentionally independent of business batch rollback.
        assertThat(rates.count()).isEqualTo(1);
    }

    @Test
    void newSuggestionCreatesOneCategoryOnlyWhenFinalBatchCommits() {
        tx.executeWithoutResult(status -> assertThat(em.createQuery(
                "select count(c) from AccountBookCategory c", Long.class).getSingleResult()).isZero());

        var result = batch.register(bookId, userId, key(), new ReceiptBatchRequestDto(List.of(
                itemWithCategory("new-category-1", "반려동물"),
                itemWithCategory("new-category-2", "반려동물"))));

        assertThat(result).hasSize(2);
        assertThat(transactions.count()).isEqualTo(2);
        tx.executeWithoutResult(status -> {
            em.clear();
            assertThat(em.createQuery(
                            "select count(c) from AccountBookCategory c where c.name = :name", Long.class)
                    .setParameter("name", "반려동물").getSingleResult()).isEqualTo(1);
        });
    }

    @Test
    void duplicateIdentifiersRollbackBatch() {
        assertThatThrownBy(() -> batch.register(bookId, userId, key(),
                new ReceiptBatchRequestDto(List.of(item("same", "JPY", "10"), item("same", "JPY", "20")))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(transactions.count()).isZero();
    }

    @Test
    void unauthorizedBatchDoesNotPersist() {
        assertThatThrownBy(
                () -> batch.register(bookId, -1L, key(), new ReceiptBatchRequestDto(List.of(item("1", "JPY", "10")))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(transactions.count()).isZero();
    }

    @Test
    void uniqueConflictRecoveryUsesCommittedWinnerAcrossConcurrentWriters() throws Exception {
        try (var pool = Executors.newFixedThreadPool(6)) {
            var start = new CountDownLatch(1);
            var tasks = java.util.stream.IntStream.range(0, 6).<Callable<Long>>mapToObj(i -> () -> {
                start.await();
                return cache.saveOrGet(
                                ExchangeRate.create("THB", "JPY", new BigDecimal("4.125"), date, date.minusDays(1), "RACE"))
                        .getId();
            }).toList();
            var futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            Set<Long> ids = new HashSet<>();
            for (var future : futures) ids.add(future.get(20, TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
            assertThat(rates.count()).isEqualTo(1);
        }
    }

    @Test
    void existingManualRegistrationAndFixedCostRemainNullableAndSupportThreeDecimals() {
        tx.executeWithoutResult(status -> {
            var book = em.find(AccountBook.class, bookId);
            book.getCurrency().update("Kuwaiti dinar", "KD", 3);
        });
        var result = manual.createTransaction(bookId,
                new AccountBookTransactionCreateRequestDto(AccountBookTransactionType.EXPENSE,
                        "Manual", null, "Food", new BigDecimal("10.125"), date, null), userId);
        assertThat(result.amount()).isEqualByComparingTo("10.125");
        assertThat(result.originalAmount()).isNull();
        tx.executeWithoutResult(status -> {
            var listing = books.search(userId, new AccountBookSearchRequestDto()).getFirst();
            assertThat(listing.currencyDecimalPlaces()).isEqualTo(3);
            assertThat(listing.expenseAmount()).isEqualByComparingTo("10.125");
            var totals = summary.getSummary(bookId, 2026, 9);
            assertThat(totals.currencyDecimalPlaces()).isEqualTo(3);
            assertThat(totals.expenseAmount()).isEqualByComparingTo("10.125");
            assertThat(transactions.aggregateMonthlyAmounts(bookId, date.withDayOfMonth(1),
                    date.plusMonths(1).withDayOfMonth(1)).getFirst().amount()).isEqualByComparingTo("10.125");
            var book = em.find(AccountBook.class, bookId);
            var fixed = AccountBookFixedCost.create(book, "Rent", null, "Home", new BigDecimal("10.125"), 12,
                    date.withDayOfMonth(1), null, null);
            em.persist(fixed);
            var generated =
                    AccountBookTransaction.createFromFixedCost(book, "Rent", null, "Home", fixed.getAmount(), date,
                            null, fixed.getId(), 2026, 9);
            em.persist(generated);
            var goal = AccountBookMonthlyGoal.create(book, 2026, 9, new BigDecimal("100.125"));
            em.persist(goal);
            em.flush();
            em.clear();
            assertThat(em.find(AccountBookFixedCost.class, fixed.getId()).getAmount()).isEqualByComparingTo("10.125");
            assertThat(em.find(AccountBookMonthlyGoal.class, goal.getId()).getGoalAmount()).isEqualByComparingTo(
                    "100.125");
            assertThat(em.find(AccountBookTransaction.class, generated.getId()).getOriginalAmount()).isNull();
        });
    }

    @Test
    void metadataEditPreservesProvenanceAndRejectsStaleFinancialChanges() {
        var created = batch.register(bookId, userId, key(), new ReceiptBatchRequestDto(List.of(item("1", "USD", "10"))))
                .getFirst();
        var edited = manual.updateTransaction(bookId, created.id(), new AccountBookTransactionUpdateRequestDto(
                        AccountBookTransactionType.EXPENSE, "Edited", null, "Food", created.amount(), date, "new memo"),
                userId);
        assertThat(edited.originalAmount()).isEqualByComparingTo("10");
        assertThatThrownBy(
                () -> manual.updateTransaction(bookId, created.id(), new AccountBookTransactionUpdateRequestDto(
                                AccountBookTransactionType.EXPENSE, "Edited", null, "Food", BigDecimal.ONE, date, null),
                        userId)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storeChartAggregatesBranchesByMerchantAndTracksStoreEdits() {
        manual.createTransaction(bookId, new AccountBookTransactionCreateRequestDto(
                AccountBookTransactionType.EXPENSE, "どらっぐ ぱぱす 船堀店", "どらっぐ ぱぱす",
                "Food", new BigDecimal("10"), date, null), userId);
        var second = manual.createTransaction(bookId, new AccountBookTransactionCreateRequestDto(
                AccountBookTransactionType.EXPENSE, "どらっぐ ぱぱす 西葛西店", "どらっぐ ぱぱす",
                "Food", new BigDecimal("20"), date, null), userId);

        var combined = transactions.aggregateExpenseAmountsByStore(
                bookId, date.withDayOfMonth(1), date.plusMonths(1).withDayOfMonth(1));
        assertThat(combined).singleElement().satisfies(row -> {
            assertThat(row.name()).isEqualTo("どらっぐ ぱぱす");
            assertThat(row.amount()).isEqualByComparingTo("30");
            assertThat(row.transactionCount()).isEqualTo(2L);
        });

        manual.updateTransaction(bookId, second.id(), new AccountBookTransactionUpdateRequestDto(
                AccountBookTransactionType.EXPENSE, second.title(), "別ブランド", "Food",
                second.amount(), date, null), userId);
        var afterEdit = transactions.aggregateExpenseAmountsByStore(
                bookId, date.withDayOfMonth(1), date.plusMonths(1).withDayOfMonth(1));
        assertThat(afterEdit).extracting(row -> row.name())
                .containsExactlyInAnyOrder("どらっぐ ぱぱす", "別ブランド");
    }

    @Test
    void adminPrecisionChangeCannotAlterRecordedReceiptAmountDuringMetadataEdit() {
        tx.executeWithoutResult(status -> em.find(AccountBook.class, bookId).getCurrency().update("Yen", "Y", 3));
        var created =
                batch.register(bookId, userId, key(), new ReceiptBatchRequestDto(List.of(item("1", "JPY", "10.125"))))
                        .getFirst();
        tx.executeWithoutResult(status -> em.find(AccountBook.class, bookId).getCurrency().update("Yen", "Y", 0));
        var edited = manual.updateTransaction(bookId, created.id(), new AccountBookTransactionUpdateRequestDto(
                AccountBookTransactionType.EXPENSE, "Metadata", null, "Food", created.amount(), date, "memo"), userId);
        assertThat(edited.amount()).isEqualByComparingTo("10.125");
        assertThat(edited.originalAmount()).isEqualByComparingTo("10.125");
    }

    @Test
    void committedResponseLossRetryReturnsOriginalBatchWithoutDuplicateTransactions() {
        var request = new ReceiptBatchRequestDto(List.of(item("1", "USD", "12.34")));
        String idempotencyKey = key();
        var first = batch.register(bookId, userId, idempotencyKey, request);
        var replay = batch.register(bookId, userId, idempotencyKey, request);
        assertThat(replay).extracting(AccountBookTransactionResponseDto::id)
                .containsExactly(first.getFirst().id());
        assertThat(transactions.count()).isEqualTo(1);
        assertThat(registrations.count()).isEqualTo(1);
    }

    @Test
    void concurrentIdenticalRetriesCommitExactlyOneBatch() throws Exception {
        var request = new ReceiptBatchRequestDto(List.of(item("1", "USD", "12.34")));
        String idempotencyKey = key();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<List<AccountBookTransactionResponseDto>> register = () -> {
                start.await();
                return batch.register(bookId, userId, idempotencyKey, request);
            };
            var first = pool.submit(register);
            var second = pool.submit(register);
            start.countDown();

            var firstResult = first.get(20, TimeUnit.SECONDS);
            var secondResult = second.get(20, TimeUnit.SECONDS);
            assertThat(secondResult).extracting(AccountBookTransactionResponseDto::id)
                    .containsExactly(firstResult.getFirst().id());
        }
        assertThat(transactions.count()).isEqualTo(1);
        assertThat(registrations.count()).isEqualTo(1);
    }

    @Test
    void reusedIdempotencyKeyWithDifferentPayloadConflicts() {
        String idempotencyKey = key();
        batch.register(bookId, userId, idempotencyKey,
                new ReceiptBatchRequestDto(List.of(item("1", "JPY", "10"))));
        assertThatThrownBy(() -> batch.register(bookId, userId, idempotencyKey,
                new ReceiptBatchRequestDto(List.of(item("1", "JPY", "11")))))
                .isInstanceOf(
                        jp.co.translacat.domain.accountbook.transaction.exception.ReceiptRegistrationException.class)
                .hasMessageContaining("different receipt batch");
        assertThat(transactions.count()).isEqualTo(1);
    }

    @Test
    void stalePreviewQuoteRollsBackWholeBatch() {
        var valid = item("1", "USD", "12.34");
        var stale = new ReceiptCandidateRequestDto(
                valid.receiptId(), valid.title(), valid.storeName(), valid.categoryName(),
                valid.originalAmount(), valid.originalCurrencyCode(), valid.transactionDate(),
                valid.memo(), "0".repeat(64));
        assertThatThrownBy(() -> batch.register(bookId, userId, key(),
                new ReceiptBatchRequestDto(List.of(stale))))
                .isInstanceOf(
                        jp.co.translacat.domain.accountbook.transaction.exception.ReceiptRegistrationException.class)
                .hasMessageContaining("reviewed again");
        assertThat(transactions.count()).isZero();
        assertThat(registrations.count()).isZero();
    }

    @Test
    void reviewAssistedDraftChangedAfterConfirmationRollsBackWholeBatch() {
        var staleReview = assisted(item("review-stale", "USD", "12.34"), 4, 3);

        assertThatThrownBy(() -> batch.register(bookId, userId, key(),
                new ReceiptBatchRequestDto(List.of(staleReview))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("changed after source confirmation");
        assertThat(transactions.count()).isZero();
        assertThat(registrations.count()).isZero();
    }

    @Test
    void reviewAssistedCurrentRevisionRegistersWithServerQuote() {
        var reviewed = assisted(item("review-ready", "USD", "12.34"), 4, 4);

        var created = batch.register(bookId, userId, key(),
                new ReceiptBatchRequestDto(List.of(reviewed)));

        assertThat(created).hasSize(1);
        assertThat(created.getFirst().originalAmount()).isEqualByComparingTo("12.34");
        assertThat(transactions.count()).isEqualTo(1);
    }

    @Test
    void previewQuoteCannotBeRegisteredIntoAnotherAccountBook() {
        var reviewedForFirstBook = item("1", "USD", "12.34");
        Long secondBookId = tx.execute(status -> {
            var user = em.find(User.class, userId);
            var currency = em.find(AccountBook.class, bookId).getCurrency();
            var secondBook = AccountBook.create(user, currency, "Other receipts", "Test");
            em.persist(secondBook);
            em.persist(AccountBookMember.createOwner(secondBook, user));
            em.flush();
            return secondBook.getId();
        });

        assertThatThrownBy(() -> batch.register(secondBookId, userId, key(),
                new ReceiptBatchRequestDto(List.of(reviewedForFirstBook))))
                .isInstanceOf(
                        jp.co.translacat.domain.accountbook.transaction.exception.ReceiptRegistrationException.class)
                .hasMessageContaining("reviewed again");
        assertThat(transactions.count()).isZero();
        assertThat(registrations.count()).isZero();
    }
}
