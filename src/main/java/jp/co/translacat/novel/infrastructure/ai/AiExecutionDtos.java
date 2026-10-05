package jp.co.translacat.novel.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public final class AiExecutionDtos {
    private AiExecutionDtos() {}

    public record Message(String role, String content) {}
    public record ModelCommand(String traceId, String instructions, List<Message> messages,
                               String tier, String reasoningEffort, String verbosity,
                               int maxOutputTokens, long remainingMilliseconds, int maxProviderCalls,
                               Map<String, Object> responseSchema, String schemaName, boolean strict, String taskName) {}
    public record ModelResult(JsonNode output, long inputTokens, long outputTokens,
                              String provider, String model, int providerCalls) {}
    public record ProfileCommand(String traceId, String instructions, List<Message> messages, String profileId,
                                 int maxOutputTokens, long remainingMilliseconds, int maxProviderCalls,
                                 Map<String, Object> responseSchema, String schemaName, boolean strict, String taskName) {}
    public record ProfileResult(JsonNode output, long inputTokens, long outputTokens, String provider, String model,
                                int providerCalls, String profileId, String profileVersion, String reasoningEffort,
                                String serviceTier, long cachedInputTokens, long reasoningTokens, Map<String, Object> metadata) {}
    public record SpeechCommand(String requestId, String text, String voice, String language,
                                String speed, long remainingMilliseconds, int maxProviderCalls) {}
    public record SpeechResult(String audioBase64, String contentType, Double durationSeconds,
                               String provider, String model, int providerCalls) {}
}
