package jp.co.translacat.domain.accountbook.transaction.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AccountBookChartTransactionFilterDto(
        @NotNull Type type,
        @NotEmpty @Size(max = 500) List<@Size(max = 120) String> keys,
        boolean exclude
) {
    public enum Type {
        CATEGORY,
        STORE
    }
}
