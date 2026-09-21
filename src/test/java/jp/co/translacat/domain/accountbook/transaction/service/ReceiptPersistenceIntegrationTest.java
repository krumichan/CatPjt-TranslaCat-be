package jp.co.translacat.domain.accountbook.transaction.service;

import jakarta.persistence.EntityManager;
import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.accountbook.repository.AccountBookRepository;
import jp.co.translacat.domain.accountbook.accountbook.repository.AccountBookSummaryRepositoryImpl;
import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookSearchRequestDto;
import jp.co.translacat.domain.accountbook.category.service.AccountBookCategoryService;
import jp.co.translacat.domain.accountbook.member.entity.AccountBookMember;
import jp.co.translacat.domain.accountbook.transaction.dto.*;
import jp.co.translacat.domain.accountbook.transaction.entity.AccountBookTransaction;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;
import jp.co.translacat.domain.accountbook.transaction.repository.AccountBookTransactionRepository;
import jp.co.translacat.domain.accountbook.fixedcost.entity.AccountBookFixedCost;
import jp.co.translacat.domain.accountbook.monthlygoal.entity.AccountBookMonthlyGoal;
import jp.co.translacat.domain.currency.entity.Currency;
import jp.co.translacat.domain.currency.entity.ExchangeRate;
import jp.co.translacat.domain.currency.repository.ExchangeRateRepository;
import jp.co.translacat.domain.currency.service.*;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.config.QueryDslConfig;
import org.junit.jupiter.api.*;
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
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:receipt-persistence;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({QueryDslConfig.class, AccountBookAccessService.class, AccountBookCategoryService.class,
        ReceiptBatchService.class, ReceiptConversionService.class, ExchangeRateService.class,
        ExchangeRateCache.class, AccountBookTransactionService.class, AccountBookSummaryRepositoryImpl.class, ReceiptPersistenceIntegrationTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReceiptPersistenceIntegrationTest {
    @TestConfiguration static class Config {
        @Bean ExchangeRateProvider fakeProvider() {
            return new ExchangeRateProvider() {
                public String name() { return "TEST"; }
                public Quote fetch(String source, String target, LocalDate requested) {
                    if (source.equals("EUR")) throw new RateUnavailableException();
                    return new Quote(new BigDecimal(source.equals("THB") ? "4.125" : "150.123456789012345678"), requested.minusDays(1));
                }
            };
        }
    }
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired ReceiptBatchService batch;
    @Autowired AccountBookTransactionService manual;
    @Autowired AccountBookTransactionRepository transactions;
    @Autowired ExchangeRateRepository rates;
    @Autowired ExchangeRateCache cache;
    @Autowired AccountBookRepository books;
    @Autowired AccountBookSummaryRepositoryImpl summary;
    TransactionTemplate tx;
    Long bookId, userId;
    final LocalDate date = LocalDate.of(2026,9,12);
    @BeforeEach void fixture() {
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> {
            var user = User.createLocalUser("receipt-"+UUID.randomUUID()+"@test.local","password","receipt",Role.USER,UUID.randomUUID().toString().substring(0,10));
            em.persist(user);
            var currency = Currency.create("JPY","Japanese Yen","Y",0,false); em.persist(currency);
            var book = AccountBook.create(user,currency,"Receipts","Test"); em.persist(book);
            em.persist(AccountBookMember.createOwner(book,user));
            em.flush(); bookId=book.getId(); userId=user.getId();
        });
    }
    @AfterEach void cleanup() {
        tx.executeWithoutResult(status -> {
            for (String name : List.of("AccountBookTransaction","AccountBookFixedCost","AccountBookMonthlyGoal","AccountBookCategory",
                    "AccountBookMember","AccountBook","ExchangeRate","Currency","User")) em.createQuery("delete from "+name).executeUpdate();
        });
    }
    ReceiptCandidateRequestDto item(String id,String source,String amount) {
        return new ReceiptCandidateRequestDto(id,"Purchase","Store","Food",new BigDecimal(amount),source,date,"Memo");
    }
    @Test void batchPersistsOriginalFactsAndRateAndQueryProjection() {
        var result=batch.register(bookId,userId,new ReceiptBatchRequestDto(List.of(item("1","USD","12.34"),item("2","THB","10.125"))));
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().amount()).isEqualByComparingTo("1853");
        assertThat(result.getFirst().originalAmount()).isEqualByComparingTo("12.34");
        assertThat(result.getFirst().originalCurrencyCode()).isEqualTo("USD");
        assertThat(result.getFirst().exchangeRate()).isEqualByComparingTo("150.123456789012345678");
        assertThat(result.getFirst().effectiveRateDate()).isEqualTo(date.minusDays(1));
        tx.executeWithoutResult(status -> {
            em.clear();
            var persisted=transactions.findById(result.getFirst().id()).orElseThrow();
            assertThat(persisted.getOriginalAmount()).isEqualByComparingTo("12.34");
            assertThat(persisted.getExchangeRate()).isEqualByComparingTo("150.123456789012345678");
            var page=transactions.findAllWithPage(bookId,new AccountBookTransactionRequestDto());
            assertThat(page.getContent()).hasSize(2).allSatisfy(row -> assertThat(row.originalAmount()).isNotNull());
            assertThat(em.createQuery("select count(c) from Currency c",Long.class).getSingleResult()).isEqualTo(1);
        });
    }
    @Test void failedSecondReceiptRollsBackFirstTransactionAndNewCategory() {
        assertThatThrownBy(() -> batch.register(bookId,userId,new ReceiptBatchRequestDto(List.of(item("1","USD","12.34"),item("2","EUR","10")))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("RATE_UNAVAILABLE");
        assertThat(transactions.count()).isZero();
        tx.executeWithoutResult(status -> assertThat(em.createQuery("select count(c) from AccountBookCategory c",Long.class).getSingleResult()).isZero());
        // Published daily cache entries are intentionally independent of business batch rollback.
        assertThat(rates.count()).isEqualTo(1);
    }
    @Test void duplicateIdentifiersRollbackBatch() {
        assertThatThrownBy(() -> batch.register(bookId,userId,new ReceiptBatchRequestDto(List.of(item("same","JPY","10"),item("same","JPY","20")))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(transactions.count()).isZero();
    }
    @Test void unauthorizedBatchDoesNotPersist() {
        assertThatThrownBy(() -> batch.register(bookId,-1L,new ReceiptBatchRequestDto(List.of(item("1","JPY","10")))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(transactions.count()).isZero();
    }
    @Test void uniqueConflictRecoveryUsesCommittedWinnerAcrossConcurrentWriters() throws Exception {
        try (var pool=Executors.newFixedThreadPool(6)) {
            var start=new CountDownLatch(1);
            var tasks=java.util.stream.IntStream.range(0,6).<Callable<Long>>mapToObj(i -> () -> {
                start.await();
                return cache.saveOrGet(ExchangeRate.create("THB","JPY",new BigDecimal("4.125"),date,date.minusDays(1),"RACE")).getId();
            }).toList();
            var futures=tasks.stream().map(pool::submit).toList(); start.countDown();
            Set<Long> ids=new HashSet<>();
            for (var future:futures) ids.add(future.get(20,TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
            assertThat(rates.count()).isEqualTo(1);
        }
    }
    @Test void existingManualRegistrationAndFixedCostRemainNullableAndSupportThreeDecimals() {
        tx.executeWithoutResult(status -> {
            var book=em.find(AccountBook.class,bookId);
            book.getCurrency().update("Kuwaiti dinar","KD",3);
        });
        var result=manual.createTransaction(bookId,new AccountBookTransactionCreateRequestDto(AccountBookTransactionType.EXPENSE,
                "Manual",null,"Food",new BigDecimal("10.125"),date,null),userId);
        assertThat(result.amount()).isEqualByComparingTo("10.125");
        assertThat(result.originalAmount()).isNull();
        tx.executeWithoutResult(status -> {
            var listing = books.search(userId, new AccountBookSearchRequestDto()).getFirst();
            assertThat(listing.currencyDecimalPlaces()).isEqualTo(3);
            assertThat(listing.expenseAmount()).isEqualByComparingTo("10.125");
            var totals = summary.getSummary(bookId, 2026, 9);
            assertThat(totals.currencyDecimalPlaces()).isEqualTo(3);
            assertThat(totals.expenseAmount()).isEqualByComparingTo("10.125");
            assertThat(transactions.aggregateMonthlyAmounts(bookId, date.withDayOfMonth(1), date.plusMonths(1).withDayOfMonth(1)).getFirst().amount()).isEqualByComparingTo("10.125");
            var book=em.find(AccountBook.class,bookId);
            var fixed=AccountBookFixedCost.create(book,"Rent",null,"Home",new BigDecimal("10.125"),12,date.withDayOfMonth(1),null,null);
            em.persist(fixed);
            var generated=AccountBookTransaction.createFromFixedCost(book,"Rent",null,"Home",fixed.getAmount(),date,null,fixed.getId(),2026,9);
            em.persist(generated);
            var goal=AccountBookMonthlyGoal.create(book,2026,9,new BigDecimal("100.125")); em.persist(goal);
            em.flush(); em.clear();
            assertThat(em.find(AccountBookFixedCost.class,fixed.getId()).getAmount()).isEqualByComparingTo("10.125");
            assertThat(em.find(AccountBookMonthlyGoal.class,goal.getId()).getGoalAmount()).isEqualByComparingTo("100.125");
            assertThat(em.find(AccountBookTransaction.class,generated.getId()).getOriginalAmount()).isNull();
        });
    }
    @Test void metadataEditPreservesProvenanceAndRejectsStaleFinancialChanges() {
        var created=batch.register(bookId,userId,new ReceiptBatchRequestDto(List.of(item("1","USD","10")))).getFirst();
        var edited=manual.updateTransaction(bookId,created.id(),new AccountBookTransactionUpdateRequestDto(
                AccountBookTransactionType.EXPENSE,"Edited",null,"Food",created.amount(),date,"new memo"),userId);
        assertThat(edited.originalAmount()).isEqualByComparingTo("10");
        assertThatThrownBy(() -> manual.updateTransaction(bookId,created.id(),new AccountBookTransactionUpdateRequestDto(
                AccountBookTransactionType.EXPENSE,"Edited",null,"Food",BigDecimal.ONE,date,null),userId)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void adminPrecisionChangeCannotAlterRecordedReceiptAmountDuringMetadataEdit() {
        tx.executeWithoutResult(status -> em.find(AccountBook.class,bookId).getCurrency().update("Yen","Y",3));
        var created=batch.register(bookId,userId,new ReceiptBatchRequestDto(List.of(item("1","JPY","10.125")))).getFirst();
        tx.executeWithoutResult(status -> em.find(AccountBook.class,bookId).getCurrency().update("Yen","Y",0));
        var edited=manual.updateTransaction(bookId,created.id(),new AccountBookTransactionUpdateRequestDto(
                AccountBookTransactionType.EXPENSE,"Metadata",null,"Food",created.amount(),date,"memo"),userId);
        assertThat(edited.amount()).isEqualByComparingTo("10.125");
        assertThat(edited.originalAmount()).isEqualByComparingTo("10.125");
    }
}
