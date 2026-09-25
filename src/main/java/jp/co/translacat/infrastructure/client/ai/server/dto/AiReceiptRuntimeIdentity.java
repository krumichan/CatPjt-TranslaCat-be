package jp.co.translacat.infrastructure.client.ai.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiReceiptRuntimeIdentity(
        @JsonProperty("run_id") String runId,
        @JsonProperty("source_fingerprint") String sourceFingerprint,
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("process_id") Long processId,
        @JsonProperty("working_directory") String workingDirectory,
        @JsonProperty("command_fingerprint") String commandFingerprint,
        @JsonProperty("git_head") String gitHead,
        @JsonProperty("provider_call_count") Long providerCallCount) {
}
