package jp.co.translacat.domain.voice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.voice.entity.VoiceSegment;
import jp.co.translacat.domain.voice.entity.VoiceSession;
import jp.co.translacat.domain.voice.entity.VoiceUsageLedger;
import jp.co.translacat.domain.voice.model.VoiceStreamContext;
import jp.co.translacat.domain.voice.repository.VoiceSegmentRepository;
import jp.co.translacat.domain.voice.repository.VoiceSessionRepository;
import jp.co.translacat.domain.voice.repository.VoiceUsageLedgerRepository;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class VoiceFinalResultCommandService {

    private final VoiceSessionRepository sessionRepository;
    private final VoiceSegmentRepository segmentRepository;
    private final VoiceUsageLedgerRepository usageRepository;
    private final VoiceStreamCommandService streamCommandService;
    private final ObjectMapper objectMapper;

    @Transactional
    public VoiceSegment persistTranscriptFinal(
            VoiceStreamContext context,
            JsonNode event,
            long globalSequence
    ) {
        String eventId = requiredText(event, "eventId");
        VoiceSegment duplicate = segmentRepository
                .findByTranscriptFinalEventId(eventId)
                .orElse(null);
        if (duplicate != null) {
            verifyIdentity(
                    duplicate,
                    context,
                    globalSequence,
                    requiredText(event, "sourceText")
            );
            return duplicate;
        }

        VoiceSegment bySequence = segmentRepository
                .findBySession_IdAndChannelAndUtteranceSequence(
                        context.sessionId(),
                        context.channel(),
                        globalSequence
                )
                .orElse(null);
        if (bySequence != null) {
            throw new BusinessException(
                    "Conflicting Final payload for Voice utterance sequence.",
                    VoiceErrorCode.FINAL_SEQUENCE_CONFLICT
            );
        }

        VoiceSession session = requireActiveSession(context);
        long speechDurationMs = longValue(event, "speechDurationMs");
        VoiceSegment segment = VoiceSegment.transcriptFinal(
                session,
                context.channel(),
                context.channel().name() + "-" + globalSequence,
                globalSequence,
                eventId,
                longValue(event, "startedAtOffsetMs"),
                longValue(event, "endedAtOffsetMs"),
                speechDurationMs,
                nullableText(event, "detectedLanguage"),
                nullableDouble(event, "languageConfidence"),
                nullableText(event, "lockedLanguage"),
                requiredText(event, "sourceText")
        );
        segmentRepository.saveAndFlush(segment);
        recordUsage(session, segment, speechDurationMs);
        updateLockedLanguage(context, event);

        return segment;
    }

    @Transactional
    public VoiceSegment persistCompleted(
            VoiceStreamContext context,
            JsonNode event,
            long globalSequence,
            long beRelayAndPersistMs
    ) {
        String completedEventId = requiredText(event, "eventId");
        VoiceSegment duplicate = segmentRepository
                .findByPipelineCompletedEventId(completedEventId)
                .orElse(null);
        if (duplicate != null) {
            verifyIdentity(
                    duplicate,
                    context,
                    globalSequence,
                    requiredText(event, "sourceText")
            );
            return duplicate;
        }

        VoiceSegment segment = segmentRepository
                .findBySession_IdAndChannelAndUtteranceSequence(
                        context.sessionId(),
                        context.channel(),
                        globalSequence
                )
                .orElseGet(() -> persistTranscriptFromCompleted(
                        context,
                        event,
                        globalSequence
                ));

        if (!segment.matchesSource(requiredText(event, "sourceText"))) {
            throw new BusinessException(
                    "Completed Voice payload does not match stored transcript.",
                    VoiceErrorCode.FINAL_PAYLOAD_CONFLICT
            );
        }
        if (!Objects.equals(
                segment.getTargetLanguage(),
                requiredText(event, "targetLanguage")
        )) {
            throw new BusinessException(
                    "Completed Voice target language does not match session snapshot.",
                    VoiceErrorCode.TARGET_LANGUAGE_CONFLICT
            );
        }

        JsonNode latency = event.path("latency");
        JsonNode model = event.path("model");
        segment.applyCompleted(
                completedEventId,
                requiredText(event, "translatedText"),
                writeJson(event.path("sourceReadingTokens")),
                event.path("translationSkipped").asBoolean(false),
                nullableLong(latency, "endpointingMs"),
                nullableLong(latency, "sttFinalizeMs"),
                nullableLong(latency, "translationMs"),
                nullableLong(latency, "aiTotalAfterSpeechMs"),
                beRelayAndPersistMs,
                nullableText(model, "sttVersion"),
                nullableText(model, "translationVersion"),
                nullableText(model, "promptVersion")
        );
        updateLockedLanguage(context, event);

        return segment;
    }

    @Transactional
    public void updateRelayLatency(
            VoiceStreamContext context,
            Long segmentId,
            long beRelayAndPersistMs
    ) {
        VoiceSegment segment = segmentRepository
                .findOwnedForUpdate(
                        segmentId,
                        context.sessionId(),
                        context.userId()
                )
                .orElseThrow(this::notFound);
        segment.updateRelayLatency(beRelayAndPersistMs);
    }

    @Transactional
    public void persistTranslationFailure(
            VoiceStreamContext context,
            JsonNode event,
            long globalSequence
    ) {
        JsonNode error = event.path("error");
        if (!"TRANSLATION".equals(nullableText(error, "stage"))
                || nullableText(event, "sourceText") == null) {
            return;
        }

        segmentRepository
                .findBySession_IdAndChannelAndUtteranceSequence(
                        context.sessionId(),
                        context.channel(),
                        globalSequence
                )
                .ifPresent(segment -> segment.markTranslationFailed(
                        nullableText(error, "code")
                ));
    }

    private VoiceSegment persistTranscriptFromCompleted(
            VoiceStreamContext context,
            JsonNode event,
            long globalSequence
    ) {
        VoiceSession session = requireActiveSession(context);
        long speechDurationMs = longValue(event, "speechDurationMs");
        VoiceSegment segment = VoiceSegment.transcriptFinal(
                session,
                context.channel(),
                context.channel().name() + "-" + globalSequence,
                globalSequence,
                null,
                longValue(event, "startedAtOffsetMs"),
                longValue(event, "endedAtOffsetMs"),
                speechDurationMs,
                nullableText(event, "detectedLanguage"),
                nullableDouble(event, "languageConfidence"),
                nullableText(event, "lockedLanguage"),
                requiredText(event, "sourceText")
        );
        segmentRepository.saveAndFlush(segment);
        recordUsage(session, segment, speechDurationMs);

        return segment;
    }

    private VoiceSession requireActiveSession(
            VoiceStreamContext context
    ) {
        VoiceSession session = sessionRepository
                .findOwnedForUpdate(
                        context.sessionId(),
                        context.userId()
                )
                .orElseThrow(this::notFound);
        if (!session.getStatus().isActiveLike()) {
            throw new BusinessException(
                    "Voice session no longer accepts Final events.",
                    VoiceErrorCode.LATE_FINAL_EVENT
            );
        }
        return session;
    }

    private void recordUsage(
            VoiceSession session,
            VoiceSegment segment,
            long speechDurationMs
    ) {
        if (usageRepository.existsBySegmentId(segment.getId())) {
            return;
        }

        usageRepository.save(
                VoiceUsageLedger.create(session.getUser(), segment)
        );
        session.addProcessedAudioMs(speechDurationMs);
    }

    private void updateLockedLanguage(
            VoiceStreamContext context,
            JsonNode event
    ) {
        String lockedLanguage = nullableText(event, "lockedLanguage");
        if (lockedLanguage != null) {
            streamCommandService.updateLockedLanguage(
                    context,
                    lockedLanguage
            );
        }
    }

    private void verifyIdentity(
            VoiceSegment segment,
            VoiceStreamContext context,
            long globalSequence,
            String sourceText
    ) {
        if (!segment.getSession().getId().equals(context.sessionId())
                || segment.getChannel() != context.channel()
                || segment.getUtteranceSequence() != globalSequence
                || !segment.matchesSource(sourceText)) {
            throw new BusinessException(
                    "Conflicting idempotent Voice Final event.",
                    VoiceErrorCode.FINAL_EVENT_CONFLICT
            );
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(
                    node.isMissingNode() || node.isNull()
                            ? objectMapper.createArrayNode()
                            : node
            );
        } catch (Exception e) {
            throw invalidEvent(
                    "Invalid Voice Reading token payload."
            );
        }
    }

    private String requiredText(
            JsonNode node,
            String field
    ) {
        String value = nullableText(node, field);
        if (value == null || value.isBlank()) {
            throw invalidEvent(
                    "Missing Voice event field: " + field
            );
        }
        return value;
    }

    private String nullableText(
            JsonNode node,
            String field
    ) {
        JsonNode value = node.get(field);
        return value == null || value.isNull()
                ? null
                : value.asText();
    }

    private Long nullableLong(
            JsonNode node,
            String field
    ) {
        JsonNode value = node.get(field);
        return value == null || value.isNull()
                ? null
                : value.asLong();
    }

    private Double nullableDouble(
            JsonNode node,
            String field
    ) {
        JsonNode value = node.get(field);
        return value == null || value.isNull()
                ? null
                : value.asDouble();
    }

    private long longValue(
            JsonNode node,
            String field
    ) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong()) {
            throw invalidEvent(
                    "Invalid Voice event numeric field: " + field
            );
        }
        return value.asLong();
    }

    private BusinessException invalidEvent(String message) {
        return new BusinessException(
                message,
                VoiceErrorCode.INVALID_EVENT_SCHEMA
        );
    }

    private BusinessException notFound() {
        return new BusinessException(
                "Voice resource was not found.",
                VoiceErrorCode.NOT_FOUND
        );
    }
}
