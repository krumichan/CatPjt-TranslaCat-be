package jp.co.translacat.domain.accountbook.analysis.service;

import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.*;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionResponseDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

public final class SpendingAnalysisSavings {
    private SpendingAnalysisSavings() { }

    public static SavingsScenario calculate(SavingsScenario proposed, List<AccountBookTransactionResponseDto> transactions,
                                             int decimals, boolean partial) {
        return calculate(proposed, transactions, decimals, partial, null);
    }

    public static SavingsScenario calculate(SavingsScenario proposed, List<AccountBookTransactionResponseDto> transactions,
                                             int decimals, boolean partial, String comment) {
        // 계산 범위와 필수 지출을 확인한 뒤 조정 가정을 선택한다.
        if (proposed == null || partial || transactions.isEmpty()) return null;
        for (var item : transactions) {
            String text = (item.title() + " " + item.category() + " " + item.memo()).toLowerCase(Locale.ROOT);
            if (text.matches("(?s).*(의료|医療|주거|住居|처방|処方|월세|家賃|정기권|교통 이용|jetbrains|openai|anthropic|업무용).*")
                    || java.util.Set.of("medical", "housing", "healthcare").contains(String.valueOf(item.category()).toLowerCase(Locale.ROOT))
                    || ((item.memo() == null || item.memo().isBlank()) && (comment == null || comment.isBlank())))
                return null;
        }
        int low, high, replacementLow, replacementHigh;
        switch (proposed.kind()) {
            case "SMALLER_SELECTION" -> {
                low = 500;
                high = 1500;
                replacementLow = 0;
                replacementHigh = 3000;
            }
            case "DRINK_REPLACEMENT" -> {
                low = 1000;
                high = 2000;
                replacementLow = 5000;
                replacementHigh = 8000;
            }
            case "SKIP_EXTRA_ITEM" -> {
                low = 10000;
                high = 10000;
                replacementLow = 0;
                replacementHigh = 0;
            }
            default -> { return null; }
        }

        // 음료 대체는 음료 구매 근거에만 적용한다. 식사·대용량 생수 전체를 카페비로 계산하지 않는다.
        if ("DRINK_REPLACEMENT".equals(proposed.kind())) {
            for (var item : transactions) {
                String text = (item.title() + " " + item.storeName() + " " + item.memo()).toLowerCase(Locale.ROOT);
                if (text.matches("(?s).*(생수|미네랄워터|天然水|ミネラルウォーター|炭酸水|탄산수|bottled water|mineral water|sparkling water|bulk water|식사|정식|定食|弁当|도시락).*")
                        || !text.matches("(?s).*(커피|음료|라떼|아메리카노|コーヒー|ラテ|coffee|latte|에스프레소|espresso|フラペチーノ|프라푸치노|콜라|주스|コーラ|ジュース|ココア|モンスター|麦茶|ブレンド|カフェラ|オーレ|cocoa|cola|soda).*")) {
                    return null;
                }
            }
        }

        // 공급자가 보낸 금액 대신 현재 기간의 실제 근거 금액과 고정 가정으로 다시 계산한다.
        BigDecimal amount = transactions.stream().map(AccountBookTransactionResponseDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gross = amount.multiply(BigDecimal.valueOf(low + high)).divide(BigDecimal.valueOf(20000), decimals, RoundingMode.HALF_UP);
        BigDecimal replacement = gross.multiply(BigDecimal.valueOf(replacementLow + replacementHigh)).divide(BigDecimal.valueOf(20000), decimals, RoundingMode.HALF_UP);
        BigDecimal minimum = amount.multiply(BigDecimal.valueOf(low)).multiply(BigDecimal.valueOf(10000 - replacementHigh))
                .divide(BigDecimal.valueOf(100000000), decimals, RoundingMode.HALF_UP);
        BigDecimal maximum = amount.multiply(BigDecimal.valueOf(high)).multiply(BigDecimal.valueOf(10000 - replacementLow))
                .divide(BigDecimal.valueOf(100000000), decimals, RoundingMode.HALF_UP);
        BigDecimal estimate = gross.subtract(replacement).max(minimum).min(maximum);
        return new SavingsScenario(minimum.toPlainString(), estimate.toPlainString(), maximum.toPlainString(),
                gross.toPlainString(), replacement.toPlainString(), low, high, replacementLow, replacementHigh,
                proposed.kind(), proposed.assumption(), amount.toPlainString());
    }

    public static SavingsScenario combine(List<Finding> findings) {
        var values = findings.stream().map(Finding::savings).filter(java.util.Objects::nonNull).toList();
        if (values.isEmpty()) return null;
        BigDecimal low = BigDecimal.ZERO, estimate = BigDecimal.ZERO, high = BigDecimal.ZERO, gross = BigDecimal.ZERO, replacement = BigDecimal.ZERO, basis = BigDecimal.ZERO;
        for (var value : values) {
            low = low.add(new BigDecimal(value.lower()));
            estimate = estimate.add(new BigDecimal(value.estimate()));
            high = high.add(new BigDecimal(value.upper()));
            gross = gross.add(new BigDecimal(value.grossReduction()));
            replacement = replacement.add(new BigDecimal(value.replacementCost()));
            basis = basis.add(new BigDecimal(value.basisAmount()));
        }
        return new SavingsScenario(low.toPlainString(), estimate.toPlainString(), high.toPlainString(), gross.toPlainString(),
                replacement.toPlainString(), 0, 0, 0, 0, "COMBINED", "DISJOINT_CITED_PURCHASES_ONLY", basis.toPlainString());
    }
}
