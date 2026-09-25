package jp.co.translacat.domain.accountbook.transaction.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReceiptBatchRequestDto(
        @NotEmpty @Size(max = 30) List<@NotNull @Valid ReceiptCandidateRequestDto> receipts) {
}
