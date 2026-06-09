package jp.co.translacat.domain.accountbook.monthlygoal.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(
        name = "account_book_monthly_goals",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_account_book_monthly_goal",
                        columnNames = {"account_book_id", "target_year", "target_month"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountBookMonthlyGoal extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 대상 가계부
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_book_id", nullable = false)
    private AccountBook accountBook;

    /**
     * 대상 연도
     */
    @Column(name = "target_year", nullable = false)
    private Integer targetYear;

    /**
     * 대상 월
     */
    @Column(name = "target_month", nullable = false)
    private Integer targetMonth;

    /**
     * 월별 목표 지출 금액
     */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal goalAmount;

    private AccountBookMonthlyGoal(
            AccountBook accountBook,
            Integer targetYear,
            Integer targetMonth,
            BigDecimal goalAmount
    ) {
        this.accountBook = accountBook;
        this.targetYear = targetYear;
        this.targetMonth = targetMonth;
        this.goalAmount = goalAmount;
    }

    public static AccountBookMonthlyGoal create(
            AccountBook accountBook,
            Integer targetYear,
            Integer targetMonth,
            BigDecimal goalAmount
    ) {
        return new AccountBookMonthlyGoal(
                accountBook,
                targetYear,
                targetMonth,
                goalAmount
        );
    }

    public void updateGoalAmount(BigDecimal goalAmount) {
        this.goalAmount = goalAmount;
    }
}