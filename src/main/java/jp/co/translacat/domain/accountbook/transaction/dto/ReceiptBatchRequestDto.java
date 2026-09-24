package jp.co.translacat.domain.accountbook.transaction.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record ReceiptBatchRequestDto(
        @NotEmpty @Size(max = 30) List<@NotNull @Valid ReceiptCandidateRequestDto> receipts) {}
