package jp.co.translacat.domain.accountbook.analysis.service;

import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.*;
import jp.co.translacat.domain.accountbook.member.entity.AccountBookMember;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:spending-analysis;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({QueryDslConfig.class, SpendingAnalysisSnapshotService.class, AccountBookAccessService.class})
class SpendingAnalysisSnapshotTest {
    @Autowired EntityManager entities;
    @Autowired SpendingAnalysisSnapshotService snapshots;
    User owner;
    AccountBook book;

    @BeforeEach
    void fixture() {
        // 준비: 운영 데이터와 무관한 테스트 소유자·통화·가계부를 만든다.
        owner = User.createLocalUser(UUID.randomUUID() + "@test.local", "password", "analysis", Role.USER,
                UUID.randomUUID().toString().substring(0, 10));
        entities.persist(owner);
        var currency = Currency.create("JPY", "Japanese Yen", "Y", 0, false);
        entities.persist(currency);
        book = AccountBook.create(owner, currency, "Analysis", "Personal");
        entities.persist(book);
        entities.persist(AccountBookMember.createOwner(book, owner));
    }

    @Test
    void monthlyPeriodIncludesLeapDayAndExcludesIncomeAndOtherPeriods() {
        // 준비
        add(LocalDate.of(2024, 2, 29), 100, "처방약");
        add(LocalDate.of(2024, 1, 31), 80, "이전 월");
        add(LocalDate.of(2024, 3, 1), 900, "다음 월");
        entities.persist(AccountBookTransaction.create(book, AccountBookTransactionType.INCOME,
                BigDecimal.valueOf(500), "Income", null, "월급", LocalDate.of(2024, 2, 2), null));

        // 실행
        var snapshot = snapshots.prepare(book.getId(), owner.getId(), new Request(PeriodType.MONTHLY, 2024, 2, "ko", " 필수 약 "));

        // 검증
        assertThat(snapshot.input().transactionCount()).isEqualTo(1);
        assertThat(new BigDecimal(snapshot.input().totalExpense())).isEqualByComparingTo("100");
        assertThat(snapshot.input().periodEndExclusive()).isEqualTo(LocalDate.of(2024, 3, 1));
        assertThat(snapshot.input().previousPeriod().transactionCount()).isEqualTo(1);
        assertThat(snapshot.input().comment()).isEqualTo("필수 약");
        assertThat(snapshot.input().transactions()).singleElement().satisfies(item -> {
            assertThat(item.date()).isEqualTo(LocalDate.of(2024, 2, 29));
            assertThat(item.refId()).isEqualTo("tx-1");
            assertThat(item.memo()).isEqualTo("처방약");
        });
    }

    @Test
    void annualPeriodHasRealYearBoundariesAndSamplesAcrossMonths() {
        // 준비
        add(LocalDate.of(2023, 12, 31), 500, "Previous year");
        for (int month = 1; month <= 12; month++) {
            add(LocalDate.of(2024, month, 1), 100, "Month " + month);
        }
        add(LocalDate.of(2025, 1, 1), 900, "Next year");

        // 실행
        var snapshot = snapshots.prepare(book.getId(), owner.getId(), new Request(PeriodType.YEARLY, 2024, null, "en", null));

        // 검증
        assertThat(snapshot.input().transactionCount()).isEqualTo(12);
        assertThat(snapshot.input().transactions()).hasSize(12);
        assertThat(new BigDecimal(snapshot.input().totalExpense())).isEqualByComparingTo("1200");
        assertThat(snapshot.input().periodStart()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(snapshot.input().periodEndExclusive()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(snapshot.input().previousPeriod().isComparable()).isTrue();
    }

    @Test
    void fingerprintDetectsMemoAndPreviousAggregateChangesWithoutChangingCurrentTotals() {
        // 준비
        var transaction = add(LocalDate.of(2024, 2, 12), 100, "Same amount");
        var previous = add(LocalDate.of(2024, 1, 12), 80, "Prior amount");
        var first = snapshots.current(book.getId(), owner.getId(), PeriodType.MONTHLY, 2024, 2);

        // 실행: 현재 기간의 메모만 바뀌어도 기존 분석은 오래된 결과다.
        transaction.update(AccountBookTransactionType.EXPENSE, BigDecimal.valueOf(100), "Transaction", "Shop",
                "생활", transaction.getTransactionDate(), "Changed reason");
        var memoChanged = snapshots.current(book.getId(), owner.getId(), PeriodType.MONTHLY, 2024, 2);
        previous.update(AccountBookTransactionType.EXPENSE, BigDecimal.valueOf(90), "Transaction", "Shop",
                "생활", previous.getTransactionDate(), "Prior amount");
        var priorChanged = snapshots.current(book.getId(), owner.getId(), PeriodType.MONTHLY, 2024, 2);

        // 검증
        assertThat(memoChanged.snapshotFingerprint()).isNotEqualTo(first.snapshotFingerprint());
        assertThat(priorChanged.snapshotFingerprint()).isNotEqualTo(memoChanged.snapshotFingerprint());
        assertThat(priorChanged.totalExpense()).isEqualTo(first.totalExpense());
        assertThat(priorChanged.transactionCount()).isEqualTo(first.transactionCount());
    }

    @Test
    void omittedAnnualTransactionsStillAffectFingerprintAndCoverageIsExplicit() {
        // 준비: 전체 상세 표본 상한 600건을 넘긴다.
        var oldest = add(LocalDate.of(2024, 1, 1), 100, "Old context");
        for (int index = 0; index < 600; index++) {
            add(LocalDate.of(2024, 1, 20), 100, "New context");
        }
        var request = new Request(PeriodType.YEARLY, 2024, null, "ko", null);
        var first = snapshots.prepare(book.getId(), owner.getId(), request);

        // 실행
        oldest.update(AccountBookTransactionType.EXPENSE, BigDecimal.valueOf(100), "Transaction", "Shop", "생활",
                oldest.getTransactionDate(), "Edited outside selected sample");
        var current = snapshots.current(book.getId(), owner.getId(), PeriodType.YEARLY, 2024, null);

        // 검증
        assertThat(first.input().coverage().isPartial()).isTrue();
        assertThat(first.input().coverage().totalTransactionCount()).isEqualTo(601);
        assertThat(first.input().coverage().analyzedTransactionCount()).isEqualTo(600);
        assertThat(first.evidence().values()).noneSatisfy(item -> assertThat(item.id()).isEqualTo(oldest.getId()));
        assertThat(current.snapshotFingerprint()).isNotEqualTo(first.fingerprint());
    }

    @Test
    void sparseYearDoesNotDropTransactionsAboveFiftyInOneMonth() {
        // 준비
        for (int index = 0; index < 75; index++) {
            add(LocalDate.of(2024, 1, 20), 100, "Recorded purpose");
        }

        // 실행
        var snapshot = snapshots.prepare(book.getId(), owner.getId(), new Request(PeriodType.YEARLY, 2024, null, "ko", null));

        // 검증
        assertThat(snapshot.input().coverage().isPartial()).isFalse();
        assertThat(snapshot.input().coverage().analyzedTransactionCount()).isEqualTo(75);
        assertThat(snapshot.input().transactions()).hasSize(75);
        assertThat(snapshot.input().previousPeriod().isComparable()).isFalse();
    }

    @Test
    void textBudgetReportsPartialCoverageAndPreservesSelectedMemos() {
        // 준비: 메모 원문을 줄이는 대신 실제 검토한 거래 수를 명시한다.
        String memo = "\"".repeat(500);
        for (int index = 0; index < 400; index++) {
            add(LocalDate.of(2024, 2, 20), 100, memo);
        }

        // 실행
        var snapshot = snapshots.prepare(book.getId(), owner.getId(), new Request(PeriodType.MONTHLY, 2024, 2, "ko", null));

        // 검증
        assertThat(snapshot.input().coverage().isPartial()).isTrue();
        assertThat(snapshot.input().coverage().reason()).isEqualTo("TEXT_BUDGET");
        assertThat(snapshot.input().coverage().analyzedTransactionCount()).isLessThan(400);
        assertThat(snapshot.input().transactions()).allSatisfy(item -> assertThat(item.memo()).isEqualTo(memo));
    }

    @Test
    void accessIsRequiredForBothSnapshotAndPaidAnalysisPreparation() {
        // 준비
        var outsider = User.createLocalUser(UUID.randomUUID() + "@test.local", "password", "outsider", Role.USER,
                UUID.randomUUID().toString().substring(0, 10));
        entities.persist(outsider);

        // 실행 / 검증
        assertThatThrownBy(() -> snapshots.current(book.getId(), outsider.getId(), PeriodType.YEARLY, 2024, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> snapshots.prepare(book.getId(), outsider.getId(),
                new Request(PeriodType.YEARLY, 2024, null, "ko", null))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inputValidationRejectsAmbiguousPeriodsAndOversizedComment() {
        // 준비
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            // 실행 / 검증
            assertThat(validator.validate(new Request(PeriodType.YEARLY, 2024, 2, "ko", null))).isNotEmpty();
            assertThat(validator.validate(new Request(PeriodType.MONTHLY, 2024, null, "ko", null))).isNotEmpty();
            assertThat(validator.validate(new Request(PeriodType.MONTHLY, 2024, 2, "ko", "x".repeat(2001)))).isNotEmpty();
            assertThat(validator.validate(new Request(PeriodType.YEARLY, 2024, null, "ja", null))).isEmpty();
        }
    }

    private AccountBookTransaction add(LocalDate date, long amount, String memo) {
        var item = AccountBookTransaction.create(book, AccountBookTransactionType.EXPENSE, BigDecimal.valueOf(amount),
                "Transaction", "Shop", "생활", date, memo);
        entities.persist(item);
        return item;
    }
}
