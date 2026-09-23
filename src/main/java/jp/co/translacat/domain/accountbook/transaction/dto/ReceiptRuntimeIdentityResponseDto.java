package jp.co.translacat.domain.accountbook.transaction.dto;

import jp.co.translacat.infrastructure.client.ai.server.dto.AiReceiptRuntimeIdentity;

import java.time.Instant;

public record ReceiptRuntimeIdentityResponseDto(
        String runId,
        String sourceFingerprint,
        Instant startedAt,
        Long processId,
        String workingDirectory,
        String commandFingerprint,
        String gitHead,
        Long providerCallCount) {
    public static ReceiptRuntimeIdentityResponseDto from(AiReceiptRuntimeIdentity value) {
        if (value == null) return null;
        return new ReceiptRuntimeIdentityResponseDto(
                value.runId(), value.sourceFingerprint(), value.startedAt(), value.processId(),
                value.workingDirectory(), value.commandFingerprint(), value.gitHead(),
                value.providerCallCount());
    }
}
