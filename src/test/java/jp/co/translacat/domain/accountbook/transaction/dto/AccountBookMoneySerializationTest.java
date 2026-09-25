package jp.co.translacat.domain.accountbook.transaction.dto;

import com.fasterxml.jackson.databind.json.JsonMapper;
import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookResponseDto;
import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookSummaryResponseDto;
import jp.co.translacat.domain.accountbook.chart.dto.AccountBookMonthlyChartItemResponseDto;
import jp.co.translacat.domain.accountbook.chart.dto.AccountBookRankingChartItemResponseDto;
import jp.co.translacat.domain.accountbook.chart.dto.AccountBookRankingChartResponseDto;
import jp.co.translacat.domain.accountbook.member.enums.AccountBookMemberRole;
import jp.co.translacat.domain.accountbook.monthlygoal.dto.AccountBookMonthlyGoalListItemResponseDto;
import jp.co.translacat.domain.accountbook.monthlygoal.dto.AccountBookMonthlyGoalResponseDto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AccountBookMoneySerializationTest {
    private static final BigDecimal EXACT = new BigDecimal("9007199254740993.125");
    private static final String EXPECTED = "9007199254740993.125";

    static Stream<Arguments> monetaryResponses() {
        return Stream.of(
                Arguments.of(
                        new AccountBookSummaryResponseDto(1L, "KWD", 3, EXACT, EXACT, EXACT, 1L),
                        List.of("incomeAmount", "expenseAmount", "balance")),
                Arguments.of(
                        new AccountBookResponseDto(1L, "Book", null, "Personal", "KWD", "KD", 3,
                                EXACT, EXACT, EXACT, 1L, AccountBookMemberRole.OWNER),
                        List.of("incomeAmount", "expenseAmount", "balance")),
                Arguments.of(
                        new AccountBookMonthlyGoalResponseDto(1L, 1L, 2026, 9, EXACT, EXACT, EXACT, 100, false),
                        List.of("goalAmount", "expenseAmount", "remainingAmount")),
                Arguments.of(
                        new AccountBookMonthlyGoalListItemResponseDto(1L, 1L, 2026, 9, EXACT, EXACT, EXACT, 100, false),
                        List.of("goalAmount", "expenseAmount", "remainingAmount")),
                Arguments.of(
                        new AccountBookMonthlyChartItemResponseDto(2026, 9, EXACT, EXACT, EXACT, EXACT),
                        List.of("incomeAmount", "expenseAmount", "balance", "expenseGoalAmount")),
                Arguments.of(
                        new AccountBookRankingChartItemResponseDto("Food", EXACT, 1L, new BigDecimal("50.5")),
                        List.of("amount")),
                Arguments.of(
                        new AccountBookRankingChartResponseDto(2026, 9, EXACT, List.of()),
                        List.of("totalAmount")));
    }

    @ParameterizedTest
    @MethodSource("monetaryResponses")
    void allResponseMoneyKeepsDecimalsBeyondJavascriptSafeInteger(Object response,
                                                                  List<String> fields) throws Exception {
        var mapper = JsonMapper.builder().build();
        var json = mapper.readTree(mapper.writeValueAsString(response));
        for (String field : fields) {
            assertThat(json.path(field).isTextual()).as(field).isTrue();
            assertThat(json.path(field).textValue()).as(field).isEqualTo(EXPECTED);
        }
        if (json.has("percentage")) {
            assertThat(json.path("percentage").isNumber()).isTrue();
            assertThat(json.path("percentage").decimalValue()).isEqualByComparingTo("50.5");
        }
        if (json.has("usageRate")) assertThat(json.path("usageRate").isIntegralNumber()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"0.00000000,0.00000000", "0.00000001,0.00000001", "9007199254740993.125,9007199254740993.125"})
    void zeroTinyAndLargeValuesNeverUseExponentNotation(String amount, String expected) throws Exception {
        var mapper = JsonMapper.builder().build();
        var value = new BigDecimal(amount);
        var response = new AccountBookSummaryResponseDto(1L, "KWD", 3, value, value, value, 1L);
        var json = mapper.readTree(mapper.writeValueAsString(response));
        assertThat(json.path("incomeAmount").textValue()).isEqualTo(expected);
        var receipt = new ReceiptConversionResponseDto(value, "USD", "JPY", value, value,
                null, null, "TEST", null, null, 3, "HALF_UP", "receipt-fx-v1",
                "a".repeat(64), "CONVERTED", false, List.of());
        var receiptJson = mapper.valueToTree(receipt);
        assertThat(receiptJson.path("originalAmount").textValue()).isEqualTo(expected);
        assertThat(receiptJson.path("convertedAmount").textValue()).isEqualTo(expected);
        assertThat(receiptJson.path("exchangeRate").textValue()).isEqualTo(expected);
    }
}
