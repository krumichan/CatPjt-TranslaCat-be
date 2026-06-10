package jp.co.translacat.domain.accountbook.transaction.dto;

public record AccountBookTransactionMonthResponseDto(
        String value,
        String label,
        Integer year,
        Integer month,
        Boolean currentMonth
) {
    public static AccountBookTransactionMonthResponseDto of(
            Integer year,
            Integer month,
            Boolean currentMonth
    ) {
        String value = "%04d-%02d".formatted(year, month);
        String label = "%04d.%02d".formatted(year, month);

        return new AccountBookTransactionMonthResponseDto(
                value,
                label,
                year,
                month,
                currentMonth
        );
    }
}