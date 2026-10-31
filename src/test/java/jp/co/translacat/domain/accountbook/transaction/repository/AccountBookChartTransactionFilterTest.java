package jp.co.translacat.domain.accountbook.transaction.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionRequestDto;
import jp.co.translacat.domain.accountbook.transaction.entity.AccountBookTransaction;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;
import jp.co.translacat.domain.currency.entity.Currency;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.config.QueryDslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:chart-transactions;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(QueryDslConfig.class)
class AccountBookChartTransactionFilterTest {
    @Autowired
    EntityManager entities;
    @Autowired
    AccountBookTransactionRepository repository;

    final ObjectMapper mapper = new ObjectMapper();
    final LocalDate october = LocalDate.of(2026, 10, 15);
    AccountBook book;
    AccountBook otherBook;

    @BeforeEach
    void fixture() {
        // 준비: 테스트 전용 소유자와 서로 다른 두 가계부를 만든다.
        var owner = User.createLocalUser("chart-" + UUID.randomUUID() + "@test.local", "password", "chart",
                Role.USER, UUID.randomUUID().toString().substring(0, 10));
        entities.persist(owner);
        var currency = Currency.create("JPY", "Japanese Yen", "Y", 0, false);
        entities.persist(currency);
        book = AccountBook.create(owner, currency, "Charts", "Test");
        otherBook = AccountBook.create(owner, currency, "Other charts", "Test");
        entities.persist(book);
        entities.persist(otherBook);
    }

    @Test
    void categoryFilterUsesExactGroupsBeforePaginationAndCount() throws Exception {
        // 준비: 유사 이름, 수입, 다른 월과 다른 가계부는 선택한 차트에 속하지 않는다.
        add(book, "생활", "Shop", 100, october, AccountBookTransactionType.EXPENSE);
        add(book, "생활", "Shop", 200, october, AccountBookTransactionType.EXPENSE);
        add(book, "생활용품", "Shop", 300, october, AccountBookTransactionType.EXPENSE);
        add(book, "생활", "Shop", 400, october, AccountBookTransactionType.INCOME);
        add(book, "생활", "Shop", 500, october.minusMonths(1), AccountBookTransactionType.EXPENSE);
        add(otherBook, "생활", "Shop", 600, october, AccountBookTransactionType.EXPENSE);
        var request = request("""
                {"year":2026,"month":10,"chartFilter":{"type":"CATEGORY","keys":["생활"]},
                 "pagination":{"page":2,"size":1,"pagingYn":true}}
                """);

        // 실행
        var page = repository.findAllWithPage(book.getId(), request);
        var chart = repository.aggregateExpenseAmountsByCategory(book.getId(), october.withDayOfMonth(1),
                october.plusMonths(1).withDayOfMonth(1));
        var unfiltered = repository.findAllWithPage(book.getId(), request("{\"year\":2026,\"month\":10}"));

        // 검증: 페이지 크기와 무관하게 선택한 그룹의 전체 건수가 유지된다.
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1).allSatisfy(item -> {
            assertThat(item.category()).isEqualTo("생활");
            assertThat(item.type()).isEqualTo(AccountBookTransactionType.EXPENSE);
        });
        assertThat(chart).filteredOn(item -> "생활".equals(item.groupKey())).singleElement()
                .satisfies(item -> {
                    assertThat(item.transactionCount()).isEqualTo(page.getTotalElements());
                    assertThat(item.amount()).isEqualByComparingTo("300");
                });
        assertThat(unfiltered.getTotalElements()).isEqualTo(4);
        assertThat(unfiltered.getContent()).anySatisfy(item ->
                assertThat(item.type()).isEqualTo(AccountBookTransactionType.INCOME));
    }

    @Test
    void combinedOtherGroupsDoNotSelectLiteralOtherCategory() throws Exception {
        // 준비: 합산 기타 항목과 이름이 같은 실제 카테고리를 구분한다.
        add(book, "생활", "Shop", 100, october, AccountBookTransactionType.EXPENSE);
        add(book, "식비", "Shop", 200, october, AccountBookTransactionType.EXPENSE);
        add(book, "기타", "Shop", 300, october, AccountBookTransactionType.EXPENSE);
        var combined = request("""
                {"chartFilter":{"type":"CATEGORY","keys":["생활","식비"]}}
                """);
        var literal = request("""
                {"chartFilter":{"type":"CATEGORY","keys":["기타"]}}
                """);

        // 실행
        var combinedPage = repository.findAllWithPage(book.getId(), combined);
        var literalPage = repository.findAllWithPage(book.getId(), literal);

        // 검증
        assertThat(combinedPage.getContent()).extracting(item -> item.category())
                .containsExactlyInAnyOrder("생활", "식비");
        assertThat(literalPage.getContent()).singleElement()
                .satisfies(item -> assertThat(item.category()).isEqualTo("기타"));
    }

    @Test
    void storeFilterMatchesNormalizedMerchantAndLegacyFallbackAcrossCategories() throws Exception {
        // 준비: 같은 점포의 표기 차이와 구버전 merchantKey 미지정 거래를 함께 집계한다.
        add(book, "생활", "ＡＭＡＺＯＮ", 100, october, AccountBookTransactionType.EXPENSE);
        add(book, "식비", "Amazon", 200, october, AccountBookTransactionType.EXPENSE);
        var legacy = add(book, "쇼핑", "AMAZON", 300, october, AccountBookTransactionType.EXPENSE);
        ReflectionTestUtils.setField(legacy, "merchantKey", null);
        add(book, "생활", "Amazon market", 400, october, AccountBookTransactionType.EXPENSE);
        add(book, "생활", null, 500, october, AccountBookTransactionType.EXPENSE);
        add(book, "생활", "Amazon", 600, october, AccountBookTransactionType.INCOME);
        entities.flush();
        var request = request("""
                {"year":2026,"month":10,"chartFilter":{"type":"STORE","keys":["amazon"]}}
                """);

        // 실행
        var page = repository.findAllWithPage(book.getId(), request);
        var chart = repository.aggregateExpenseAmountsByStore(book.getId(), october.withDayOfMonth(1),
                october.plusMonths(1).withDayOfMonth(1));

        // 검증: 표시명 대신 차트의 정규화 키를 사용하며 금액과 건수가 일치한다.
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(item -> item.category())
                .containsExactlyInAnyOrder("생활", "식비", "쇼핑");
        assertThat(chart).filteredOn(item -> "amazon".equals(item.groupKey())).singleElement()
                .satisfies(item -> {
                    assertThat(item.transactionCount()).isEqualTo(page.getTotalElements());
                    assertThat(item.amount()).isEqualByComparingTo("600");
                });
    }

    @Test
    void allTimeAndMissingKeysDoNotBroadenTheSelectedGroup() throws Exception {
        // 준비: 기간 생략은 전체 기간이며 존재하지 않는 null 키는 전체 목록을 뜻하지 않는다.
        add(book, "생활", "Shop", 100, october, AccountBookTransactionType.EXPENSE);
        add(book, "생활", "Shop", 200, october.minusYears(1), AccountBookTransactionType.EXPENSE);
        var blankCategory = add(book, "Legacy", "Shop", 300, october, AccountBookTransactionType.EXPENSE);
        ReflectionTestUtils.setField(blankCategory, "category", "");
        entities.flush();
        var selected = request("""
                {"chartFilter":{"type":"CATEGORY","keys":[null,"생활"]}}
                """);
        var missing = request("""
                {"chartFilter":{"type":"CATEGORY","keys":[null]}}
                """);
        var unnamedStore = request("""
                {"chartFilter":{"type":"STORE","keys":[null,""]}}
                """);
        var blank = request("""
                {"chartFilter":{"type":"CATEGORY","keys":[""]}}
                """);

        // 실행
        var selectedPage = repository.findAllWithPage(book.getId(), selected);
        var missingPage = repository.findAllWithPage(book.getId(), missing);
        var unnamedStorePage = repository.findAllWithPage(book.getId(), unnamedStore);
        var blankPage = repository.findAllWithPage(book.getId(), blank);

        // 검증
        assertThat(selectedPage.getTotalElements()).isEqualTo(2);
        assertThat(missingPage.getContent()).isEmpty();
        assertThat(unnamedStorePage.getContent()).isEmpty();
        assertThat(blankPage.getContent()).singleElement()
                .satisfies(item -> assertThat(item.category()).isEmpty());
    }

    @Test
    void nestedFilterValidationRejectsMissingTypeAndEmptyKeys() throws Exception {
        // 준비
        var request = request("""
                {"chartFilter":{"keys":[]}}
                """);

        // 실행
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(request);

            // 검증
            assertThat(violations).extracting(item -> item.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("chartFilter.type", "chartFilter.keys");
        }
    }

    @Test
    void excludedTopGroupsSelectRemainingExpenseGroupsAndPreserveBlankCategory() throws Exception {
        // 준비: 합산 기타는 실제 이름이 기타인 카테고리와 구버전 빈 카테고리도 포함한다.
        add(book, "생활", "Shop", 100, october, AccountBookTransactionType.EXPENSE);
        add(book, "식비", "Shop", 200, october, AccountBookTransactionType.EXPENSE);
        add(book, "기타", "Shop", 300, october, AccountBookTransactionType.EXPENSE);
        var blankCategory = add(book, "Legacy", "Shop", 400, october, AccountBookTransactionType.EXPENSE);
        ReflectionTestUtils.setField(blankCategory, "category", "");
        add(book, "기타", "Shop", 500, october, AccountBookTransactionType.INCOME);
        entities.flush();
        var remaining = request("""
                {"chartFilter":{"type":"CATEGORY","keys":["생활","식비"],"exclude":true}}
                """);
        var excludingNull = request("""
                {"chartFilter":{"type":"CATEGORY","keys":[null,"생활","식비"],"exclude":true}}
                """);

        // 실행
        var remainingPage = repository.findAllWithPage(book.getId(), remaining);
        var excludingNullPage = repository.findAllWithPage(book.getId(), excludingNull);

        // 검증: 상위 키의 수만 전달하며 남은 그룹의 거래만 반환한다.
        assertThat(remainingPage.getContent()).extracting(item -> item.category())
                .containsExactlyInAnyOrder("기타", "");
        assertThat(excludingNullPage.getContent()).extracting(item -> item.category())
                .containsExactlyInAnyOrder("기타", "");
    }

    @Test
    void excludedStoreGroupsStillExcludeUnnamedStoresAndIncome() throws Exception {
        // 준비
        add(book, "생활", "Amazon", 100, october, AccountBookTransactionType.EXPENSE);
        add(book, "생활", "Shop", 200, october, AccountBookTransactionType.EXPENSE);
        add(book, "생활", "Elsewhere", 300, october, AccountBookTransactionType.EXPENSE);
        add(book, "생활", null, 400, october, AccountBookTransactionType.EXPENSE);
        add(book, "생활", "Elsewhere", 500, october, AccountBookTransactionType.INCOME);
        var request = request("""
                {"chartFilter":{"type":"STORE","keys":["amazon","shop"],"exclude":true}}
                """);

        // 실행
        var page = repository.findAllWithPage(book.getId(), request);

        // 검증
        assertThat(page.getContent()).singleElement()
                .satisfies(item -> assertThat(item.storeName()).isEqualTo("Elsewhere"));
    }

    private AccountBookTransaction add(AccountBook targetBook, String category, String store, long amount,
                                       LocalDate date, AccountBookTransactionType type) {
        var transaction = AccountBookTransaction.create(targetBook, type, BigDecimal.valueOf(amount),
                "Transaction", store, category, date, null);
        entities.persist(transaction);
        return transaction;
    }

    private AccountBookTransactionRequestDto request(String json) throws Exception {
        return mapper.readValue(json, AccountBookTransactionRequestDto.class);
    }
}
