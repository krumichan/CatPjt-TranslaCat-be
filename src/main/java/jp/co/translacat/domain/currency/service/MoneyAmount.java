package jp.co.translacat.domain.currency.service;

import jp.co.translacat.domain.currency.entity.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyAmount {
    private MoneyAmount() {}

    public static BigDecimal normalize(BigDecimal amount, Currency currency) {
        if (amount == null || amount.signum() < 0)
            throw new IllegalArgumentException("Amount must not be negative.");
        if ((long) amount.precision() - amount.scale() > 20)
            throw new IllegalArgumentException("Amount is too large.");
        int decimals = currency.getDecimalPlaces();
        if (decimals < 0 || decimals > 8)
            throw new IllegalArgumentException("Currency decimal places must be between 0 and 8.");
        BigDecimal value = amount.setScale(decimals, RoundingMode.HALF_UP);
        if (value.precision() - value.scale() > 20)
            throw new IllegalArgumentException("Amount is too large.");
        return value;
    }

    public static BigDecimal positive(BigDecimal amount, Currency currency) {
        BigDecimal value = normalize(amount, currency);
        if (value.signum() <= 0)
            throw new IllegalArgumentException(
                    "Amount rounds to zero in the account-book currency.");
        return value;
    }
}
