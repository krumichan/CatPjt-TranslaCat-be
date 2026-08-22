package jp.co.translacat.domain.voice.websocket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jp.co.translacat.domain.voice.entity.VoiceSegment;
import jp.co.translacat.domain.voice.enums.VoiceAiEventType;
import jp.co.translacat.domain.voice.service.VoiceFinalResultCommandService;
import jp.co.translacat.domain.voice.service.VoiceStreamCommandService;
import jp.co.translacat.domain.voice.service.VoiceUsageQueryService;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class VoiceAiEventProcessor {

    private final ObjectMapper objectMapper;
    private final VoiceFinalResultCommandService finalResultCommandService;
    private final VoiceStreamCommandService streamCommandService;
    private final VoiceUsageQueryService usageQueryService;

    public ProcessedEvent process(
            VoiceRelayConnection connection,
            String rawJson
    ) {
        ObjectNode event = parseObject(rawJson);
        VoiceAiEventType type = parseEventType(
                requiredText(event, "type")
        );

        requiredText(event, "eventId");
        validateScope(connection, event);

        Long globalSequence = remapSequence(connection, event);
        if (globalSequence != null) {
            event.put("utteranceSequence", globalSequence);
            event.put(
                    "utteranceKey",
                    connection.context().channel().name()
                            + "-"
                            + globalSequence
            );
        }

        return switch (type) {
            case STREAM_READY -> processStreamReady(
                    connection,
                    event
            );
            case TRANSCRIPT_PARTIAL -> processPartial(
                    connection,
                    event,
                    globalSequence
            );
            case TRANSCRIPT_FINAL -> processFinal(
                    connection,
                    event,
                    globalSequence
            );
            case VOICE_PIPELINE_COMPLETED -> processCompleted(
                    connection,
                    event,
                    globalSequence
            );
            case VOICE_PIPELINE_FAILED -> processFailed(
                    connection,
                    event,
                    globalSequence
            );
            case NO_SPEECH -> processNoSpeech(
                    connection,
                    event,
                    globalSequence
            );
            case BACKPRESSURE -> processBackpressure(
                    connection,
                    event
            );
            case STREAM_CLOSED -> processStreamClosed(
                    connection,
                    event
            );
            case SPEECH_STARTED -> normal(
                    VoiceAiEventType.SPEECH_STARTED,
                    event
            );
        };
    }

    private ProcessedEvent processStreamReady(
            VoiceRelayConnection connection,
            ObjectNode event
    ) {
        streamCommandService.markStreaming(connection.context());
        return normal(VoiceAiEventType.STREAM_READY, event);
    }

    private ProcessedEvent processPartial(
            VoiceRelayConnection connection,
            ObjectNode event,
            Long globalSequence
    ) {
        requireSequence(globalSequence, "Partial");
        int revision = event.path("revision").asInt(-1);
        requiredText(event, "sourceText");

        if (!connection.acceptPartial(globalSequence, revision)) {
            return ProcessedEvent.suppressed(VoiceAiEventType.TRANSCRIPT_PARTIAL);
        }
        return normal(VoiceAiEventType.TRANSCRIPT_PARTIAL, event);
    }

    private ProcessedEvent processFinal(
            VoiceRelayConnection connection,
            ObjectNode event,
            Long globalSequence
    ) {
        requireSequence(globalSequence, "Final");
        finalResultCommandService.persistTranscriptFinal(
                connection.context(),
                event,
                globalSequence
        );
        connection.markFinalized(globalSequence);

        return normal(VoiceAiEventType.TRANSCRIPT_FINAL, event);
    }

    private ProcessedEvent processCompleted(
            VoiceRelayConnection connection,
            ObjectNode event,
            Long globalSequence
    ) {
        requireSequence(globalSequence, "Completed");

        long startedNanos = System.nanoTime();
        VoiceSegment segment = finalResultCommandService.persistCompleted(
                connection.context(),
                event,
                globalSequence,
                0L
        );
        long relayPersistMs = Math.max(
                0L,
                TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - startedNanos
                )
        );
        finalResultCommandService.updateRelayLatency(
                connection.context(),
                segment.getId(),
                relayPersistMs
        );
        connection.markFinalized(globalSequence);

        event.remove("model");
        ObjectNode latency = event.with("latency");
        latency.put("beRelayAndPersistMs", relayPersistMs);
        long aiMs = latency.path("aiTotalAfterSpeechMs")
                .asLong(0L);
        latency.put("totalAfterSpeechMs", aiMs + relayPersistMs);

        boolean limitReached = usageQueryService.isLimitReached(
                connection.context().userId(),
                connection.context().sessionId()
        );

        return new ProcessedEvent(
                VoiceAiEventType.VOICE_PIPELINE_COMPLETED,
                write(event),
                false,
                limitReached
        );
    }

    private ProcessedEvent processFailed(
            VoiceRelayConnection connection,
            ObjectNode event,
            Long globalSequence
    ) {
        if (globalSequence != null) {
            finalResultCommandService.persistTranslationFailure(
                    connection.context(),
                    event,
                    globalSequence
            );
            connection.markFinalized(globalSequence);
        }

        return normal(VoiceAiEventType.VOICE_PIPELINE_FAILED, event);
    }

    private ProcessedEvent processNoSpeech(
            VoiceRelayConnection connection,
            ObjectNode event,
            Long globalSequence
    ) {
        if (globalSequence != null) {
            connection.markFinalized(globalSequence);
        }

        return normal(VoiceAiEventType.NO_SPEECH, event);
    }

    private ProcessedEvent processBackpressure(
            VoiceRelayConnection connection,
            ObjectNode event
    ) {
        streamCommandService.markBackpressured(
                connection.context()
        );
        return normal(VoiceAiEventType.BACKPRESSURE, event);
    }

    private ProcessedEvent processStreamClosed(
            VoiceRelayConnection connection,
            ObjectNode event
    ) {
        streamCommandService.markDisconnected(
                connection.context()
        );
        return new ProcessedEvent(
                VoiceAiEventType.STREAM_CLOSED,
                write(event),
                true,
                false
        );
    }

    private ProcessedEvent normal(
            VoiceAiEventType type,
            ObjectNode event
    ) {
        return new ProcessedEvent(
                type,
                write(event),
                false,
                false
        );
    }

    private VoiceAiEventType parseEventType(String type) {
        try {
            return VoiceAiEventType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw invalidSchema(
                    "Unsupported Voice AI event type: " + type
            );
        }
    }

    private Long remapSequence(
            VoiceRelayConnection connection,
            ObjectNode event
    ) {
        JsonNode value = event.get("utteranceSequence");
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.canConvertToLong()) {
            throw invalidSchema(
                    "utteranceSequence must be an integer."
            );
        }

        return connection.globalSequence(value.asLong());
    }

    private void validateScope(
            VoiceRelayConnection connection,
            ObjectNode event
    ) {
        if (!connection.context().sessionId()
                .equals(requiredText(event, "sessionId"))
                || !connection.context().channel().name()
                .equals(requiredText(event, "channel"))) {
            throw new BusinessException(
                    "Voice AI event scope does not match the public stream.",
                    VoiceErrorCode.AI_EVENT_SCOPE_MISMATCH
            );
        }
    }

    private ObjectNode parseObject(String rawJson) {
        try {
            JsonNode node = objectMapper.readTree(rawJson);
            if (!node.isObject()) {
                throw invalidSchema(
                        "Voice AI event must be a JSON object."
                );
            }
            return (ObjectNode) node;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw invalidSchema(
                    "Voice AI event is not valid JSON."
            );
        }
    }

    private String requiredText(
            JsonNode event,
            String field
    ) {
        JsonNode value = event.get(field);
        if (value == null
                || value.isNull()
                || !value.isTextual()
                || value.asText().isBlank()) {
            throw invalidSchema(
                    "Missing Voice AI event field: " + field
            );
        }
        return value.asText();
    }

    private void requireSequence(
            Long sequence,
            String eventName
    ) {
        if (sequence == null) {
            throw invalidSchema(
                    eventName + " event requires utteranceSequence."
            );
        }
    }

    private String write(JsonNode event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw invalidSchema(
                    "Failed to serialize Voice public event."
            );
        }
    }

    private BusinessException invalidSchema(String message) {
        return new BusinessException(
                message,
                VoiceErrorCode.INVALID_EVENT_SCHEMA
        );
    }

    public record ProcessedEvent(
            VoiceAiEventType type,
            String publicJson,
            boolean streamClosed,
            boolean usageLimitReached
    ) {
        public static ProcessedEvent suppressed(
                VoiceAiEventType type
        ) {
            return new ProcessedEvent(
                    type,
                    null,
                    false,
                    false
            );
        }

        public boolean suppressed() {
            return publicJson == null;
        }
    }
}
