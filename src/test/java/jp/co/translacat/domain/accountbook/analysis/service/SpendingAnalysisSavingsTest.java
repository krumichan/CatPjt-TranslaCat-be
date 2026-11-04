package jp.co.translacat.domain.accountbook.analysis.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.SavingsScenario;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionResponseDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpendingAnalysisSavingsTest {
    private AccountBookTransactionResponseDto purchase(String amount, String title, String memo) throws Exception {
        return JsonMapper.builder().findAndAddModules().build().readValue(
                "{\"id\":1,\"accountBookId\":1,\"type\":\"EXPENSE\",\"amount\":\"" + amount
                        + "\",\"title\":\"" + title + "\",\"memo\":\"" + memo + "\",\"category\":\"쇼핑\"}",
                AccountBookTransactionResponseDto.class);
    }

    private SavingsScenario proposal(String kind) {
        return new SavingsScenario("999999", "999999", "999999", "999999", "0", 9999, 9999,
                0, 0, kind, "선택 구매의 일부를 조정하는 가정");
    }

    @Test
    void ignoresProviderAmountsAndSubtractsReplacementCost() throws Exception {
        // 준비: 실제 구매 근거와 다른 공급자 금액을 보낸다.
        var purchases = List.of(purchase("10000", "커피", "아이스 라떼"));

        // 실행
        var result = SpendingAnalysisSavings.calculate(proposal("DRINK_REPLACEMENT"), purchases, 0, false);

        // 검증: 15% 감소 가정의 중간값에서 대체 음료 비용 65%를 뺀다.
        assertThat(result.basisAmount()).isEqualTo("10000");
        assertThat(result.grossReduction()).isEqualTo("1500");
        assertThat(result.replacementCost()).isEqualTo("975");
        assertThat(result.estimate()).isEqualTo("525");
        assertThat(result.lower()).isEqualTo("200");
        assertThat(result.upper()).isEqualTo("1000");
    }

    @Test
    void preservesLargeDecimalAmountsWithoutFloatingPoint() throws Exception {
        // 준비
        var purchases = List.of(purchase("9007199254740993.125", "장식", "추가 장식 구매"));

        // 실행
        var result = SpendingAnalysisSavings.calculate(proposal("SMALLER_SELECTION"), purchases, 3, false);

        // 검증
        assertThat(result.basisAmount()).isEqualTo("9007199254740993.125");
        assertThat(result.estimate()).isEqualTo("765611936652984.416");
    }

    @Test
    void partialUnknownProtectedAndMixedMealPurchasesHaveNoForecast() throws Exception {
        // 준비
        var drink = purchase("1000", "카페", "커피");

        // 실행 / 검증: 산정 불가를 0원 절약으로 바꾸지 않는다.
        assertThat(SpendingAnalysisSavings.calculate(proposal("DRINK_REPLACEMENT"), List.of(drink), 0, true)).isNull();
        assertThat(SpendingAnalysisSavings.calculate(proposal("SKIP_EXTRA_ITEM"), List.of(purchase("1000", "일반 결제", "")), 0, false)).isNull();
        assertThat(SpendingAnalysisSavings.calculate(proposal("SKIP_EXTRA_ITEM"), List.of(purchase("1000", "약국", "처방약")), 0, false)).isNull();
        assertThat(SpendingAnalysisSavings.calculate(proposal("DRINK_REPLACEMENT"), List.of(drink, purchase("2000", "식당", "정식과 커피")), 0, false)).isNull();
    }

    @Test
    void knownSoftDrinksAndWaterAreDistinguishedByContents() throws Exception {
        // 준비
        var cola = purchase("10000", "Amazon コカ・コーラ", "コカ・コーラ ゼロ 24本");
        var water = purchase("10000", "Amazon 天然水", "天然水 24本");

        // 실행 / 검증
        assertThat(SpendingAnalysisSavings.calculate(proposal("DRINK_REPLACEMENT"), List.of(cola), 0, false).estimate()).isEqualTo("525");
        assertThat(SpendingAnalysisSavings.calculate(proposal("DRINK_REPLACEMENT"), List.of(water), 0, false)).isNull();
    }
}
