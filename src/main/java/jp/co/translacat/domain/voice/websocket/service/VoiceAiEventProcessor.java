package jp.co.translacat.domain.voice.websocket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jp.co.translacat.domain.voice.entity.VoiceSegment;
import jp.co.translacat.domain.voice.enums.VoiceAiEventType;
import jp.co.translacat.domain.voice.service.VoiceFinalResultCommandService;
import jp.co.translacat.domain.voice.service.VoiceSourceReadingService;
import jp.co.translacat.domain.voice.service.VoiceStreamCommandService;
import jp.co.translacat.domain.voice.service.VoiceUsageQueryService;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class VoiceAiEventProcessor {

    private static final int MAX_PREVIEW_TEXT_LENGTH = 10_000;
    private static final long MAX_SAFE_REVISION = 9_007_199_254_740_991L;

    private final ObjectMapper objectMapper;
    private final VoiceFinalResultCommandService finalResultCommandService;
    private final VoiceStreamCommandService streamCommandService;
    private final VoiceUsageQueryService usageQueryService;
    private final VoiceSourceReadingService sourceReadingService;

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
        // 연결 준비 중 종료한 채널을 늦은 ready 이벤트로 다시 활성화하지 않는다.
        if (connection.isCloseRequested()) {
            return ProcessedEvent.suppressed(VoiceAiEventType.STREAM_READY);
        }

        streamCommandService.markStreaming(connection.context());
        return normal(VoiceAiEventType.STREAM_READY, event);
    }

    private ProcessedEvent processPartial(
            VoiceRelayConnection connection,
            ObjectNode event,
            Long globalSequence
    ) {
        requireSequence(globalSequence, "Partial");
        long revision = requiredPreviewRevision(event);
        validatePreviewText(requiredText(event, "sourceText"), "sourceText");

        // 임시 번역은 원문과 같은 revision의 전체 스냅샷이다. 저장과 사용량에는 반영하지 않는다.
        JsonNode translatedText = event.get("translatedText");
        boolean hasTranslation = translatedText != null && !translatedText.isNull();
        if (hasTranslation) {
            validatePreviewText(requiredText(event, "translatedText"), "translatedText");
        }
        JsonNode targetLanguage = event.get("targetLanguage");
        if (hasTranslation || (targetLanguage != null && !targetLanguage.isNull())) {
            if (!connection.context().targetLanguage().equals(requiredText(event, "targetLanguage"))) {
                throw invalidSchema("Partial target language does not match the Voice session.");
            }
        }

        // 확정 결과 또는 더 최신 스냅샷이 도착한 발화를 늦은 preview로 되돌리지 않는다.
        if (!connection.acceptPartial(globalSequence, revision)) {
            return ProcessedEvent.suppressed(VoiceAiEventType.TRANSCRIPT_PARTIAL);
        }
        return normal(VoiceAiEventType.TRANSCRIPT_PARTIAL, event);
    }

    private long requiredPreviewRevision(ObjectNode event) {
        JsonNode value = event.get("revision");
        if (value == null
                || !value.isIntegralNumber()
                || !value.canConvertToLong()
                || value.asLong() < 1L
                || value.asLong() > MAX_SAFE_REVISION) {
            throw invalidSchema("Partial revision must be a positive safe integer.");
        }
        return value.asLong();
    }

    private void validatePreviewText(String value, String field) {
        if (value.codePointCount(0, value.length()) > MAX_PREVIEW_TEXT_LENGTH) {
            throw invalidSchema("Voice preview field is too long: " + field);
        }
    }

    private ProcessedEvent processFinal(
            VoiceRelayConnection connection,
            ObjectNode event,
            Long globalSequence
    ) {
        requireSequence(globalSequence, "Final");
        // 확정 원문을 먼저 로컬 Sudachi로 분석해 번역 대기 중인 화면과 저장 이력에 즉시 반영한다.
        event.set("sourceReadingTokens", sourceReadingService.forFinal(connection.context(), event));
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
        // AI의 읽기 토큰에 의존하지 않고 완료 이벤트와 DB에 동일한 BE 생성 결과를 기록한다.
        event.set("sourceReadingTokens", sourceReadingService.forFinal(connection.context(), event));
        VoiceSegment segment = finalResultCommandService.persistCompleted(
                connection.context(),
                event,
                globalSequence,
                0L
        );
        // 확정 원문의 저장된 읽기를 재사용한 경우에도 public 완료 이벤트를 DB 결과와 일치시킨다.
        try {
            if (segment.getSourceReadingTokens() != null) {
                event.set("sourceReadingTokens", objectMapper.readTree(segment.getSourceReadingTokens()));
            }
        } catch (Exception ignored) {
            // 읽기 JSON의 손상은 번역 완료 이벤트를 막지 않는다.
        }
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
        // STT 단계 실패는 원문/DB 행이 없을 수 있으므로 내용 없이 세션과 실패 단계만 추적한다.
        JsonNode error = event.path("error");
        log.warn(
                "Voice pipeline failed session={} channel={} sequence={} code={} stage={} retryable={}",
                connection.context().sessionId(),
                connection.context().channel(),
                globalSequence,
                error.path("code").asText("UNKNOWN"),
                error.path("stage").asText("UNKNOWN"),
                error.path("retryable").asBoolean(false)
        );

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
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
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
