package jp.co.translacat.domain.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jp.co.translacat.domain.voice.config.VoicePolicyProperties;
import jp.co.translacat.domain.voice.entity.VoiceSegment;
import jp.co.translacat.domain.voice.entity.VoiceSession;
import jp.co.translacat.domain.voice.enums.*;
import jp.co.translacat.domain.voice.mapper.VoiceResponseMapper;
import jp.co.translacat.domain.voice.model.VoiceStreamContext;
import jp.co.translacat.domain.voice.model.VoiceTranslationRetryContext;
import jp.co.translacat.domain.voice.repository.*;
import jp.co.translacat.domain.voice.service.*;
import jp.co.translacat.domain.voice.websocket.service.VoiceAiEventProcessor;
import jp.co.translacat.domain.voice.websocket.service.VoiceRelayConnection;
import jp.co.translacat.infrastructure.client.ai.server.voice.dto.AiVoiceTranslationRetryResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VoiceSourceReadingFlowTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final VoiceStreamContext context = new VoiceStreamContext(1L, "session", VoiceChannel.REMOTE,
            "connection", VoiceMode.MEDIA, VoiceSourceLanguageMode.AUTO, null, "ja", "ko", "{}", 0);

    @Test
    void finalAndCompletedEventsReplaceAiReadingsBeforeRelayAndPersistence() throws Exception {
        // 준비: AI가 보낸 읽기와 BE의 Sudachi 결과를 일부러 다르게 만든다.
        var sourceReadings = mock(VoiceSourceReadingService.class);
        ArrayNode local = localReadings();
        when(sourceReadings.forFinal(eq(context), any())).thenReturn(local);
        var finals = mock(VoiceFinalResultCommandService.class);
        var segment = mock(VoiceSegment.class);
        when(segment.getId()).thenReturn(7L);
        when(segment.getSourceReadingTokens()).thenReturn(local.toString());
        when(finals.persistCompleted(eq(context), any(), eq(1L), eq(0L))).thenReturn(segment);
        var processor = new VoiceAiEventProcessor(mapper, finals,
                mock(VoiceStreamCommandService.class), mock(VoiceUsageQueryService.class), sourceReadings);
        var relay = new VoiceRelayConnection(context, mock(WebSocketSession.class), policy(), error -> {
            throw new AssertionError(error);
        });
        ObjectNode transcript = event("TRANSCRIPT_FINAL", "transcript-1");
        ObjectNode completed = event("VOICE_PIPELINE_COMPLETED", "completed-1");
        completed.put("targetLanguage", "ko");
        completed.put("translatedText", "도쿄");
        completed.putObject("latency").put("aiTotalAfterSpeechMs", 25);

        // 실행
        var finalEvent = mapper.readTree(processor.process(relay, transcript.toString()).publicJson());
        var completedEvent = mapper.readTree(processor.process(relay, completed.toString()).publicJson());

        // 검증: 처음 보이는 확정 카드와 마지막 완료 카드 모두 로컬 읽기만 가진다.
        assertThat(finalEvent.path("sourceReadingTokens")).isEqualTo(local);
        assertThat(completedEvent.path("sourceReadingTokens")).isEqualTo(local);
        var savedFinal = ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
        verify(finals).persistTranscriptFinal(eq(context), savedFinal.capture(), eq(1L));
        assertThat(savedFinal.getValue().path("sourceReadingTokens")).isEqualTo(local);
        var savedCompleted = ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
        verify(finals).persistCompleted(eq(context), savedCompleted.capture(), eq(1L), eq(0L));
        assertThat(savedCompleted.getValue().path("sourceReadingTokens")).isEqualTo(local);
    }

    @Test
    void transcriptFinalSavesReadingsBeforeTranslationFinishes() throws Exception {
        // 준비: 트랜잭션의 저장 경계만 실제 구현으로 실행한다.
        var sessions = mock(VoiceSessionRepository.class);
        var segments = mock(VoiceSegmentRepository.class);
        var usage = mock(VoiceUsageLedgerRepository.class);
        var session = mock(VoiceSession.class);
        when(session.getStatus()).thenReturn(VoiceSessionStatus.ACTIVE);
        when(session.getTargetLanguage()).thenReturn("ko");
        when(session.getId()).thenReturn("session");
        when(sessions.findOwnedForUpdate("session", 1L)).thenReturn(Optional.of(session));
        var service = new VoiceFinalResultCommandService(sessions, segments, usage,
                mock(VoiceStreamCommandService.class), mapper, mock(VoiceSourceReadingService.class));
        ObjectNode event = event("TRANSCRIPT_FINAL", "transcript-1");
        event.set("sourceReadingTokens", localReadings());
        event.put("startedAtOffsetMs", 0);
        event.put("endedAtOffsetMs", 1_000);
        event.put("speechDurationMs", 1_000);

        // 실행
        VoiceSegment stored = service.persistTranscriptFinal(context, event, 1);

        // 검증: 번역 완료 전에도 이력 세그먼트가 원문 읽기를 보유한다.
        assertThat(mapper.readTree(stored.getSourceReadingTokens())).isEqualTo(localReadings());
        verify(segments).saveAndFlush(stored);
    }

    @Test
    void retryIgnoresAiReadingTokensAndPreservesValidStoredReadingsOnLocalFailure() throws Exception {
        // 준비: 재시도 번역은 성공하지만 일시적으로 로컬 읽기 분석이 빈 목록을 돌려준다.
        var segment = mock(VoiceSegment.class);
        when(segment.getStatus()).thenReturn(VoiceSegmentStatus.TRANSLATION_FAILED);
        when(segment.getSourceText()).thenReturn("東京");
        when(segment.getSourceReadingTokens()).thenReturn(localReadings().toString());
        var segments = mock(VoiceSegmentRepository.class);
        when(segments.findOwnedForUpdate(7L, "session", 1L)).thenReturn(Optional.of(segment));
        var readings = mock(VoiceSourceReadingService.class);
        when(readings.forLanguage("東京", "ja")).thenReturn(mapper.createArrayNode());
        when(readings.preserveStoredWhenGenerationFails("[]", localReadings().toString(), "東京"))
                .thenReturn(localReadings().toString());
        var service = new VoiceTranslationRetryCommandService(segments,
                mock(VoiceResponseMapper.class), mapper, readings);
        AiVoiceTranslationRetryResponse response = mock(AiVoiceTranslationRetryResponse.class);
        when(response.translatedText()).thenReturn("도쿄");
        when(response.sourceReadingTokens()).thenReturn(mapper.createArrayNode().addObject()
                .put("surface", "wrong").put("reading", "wrong"));

        // 실행
        service.apply(1L, "session", 7L, new VoiceTranslationRetryContext("東京", "ja", "ko"), response);

        // 검증: 번역 성공은 유지하면서 검증된 기존 읽기를 보존하고 AI 읽기는 사용하지 않는다.
        verify(segment).applyRetry(eq("도쿄"), eq(localReadings().toString()), anyBoolean(), any(), any());
        verify(response, never()).sourceReadingTokens();
    }

    private ObjectNode event(String type, String eventId) {
        ObjectNode event = mapper.createObjectNode();
        event.put("type", type);
        event.put("eventId", eventId);
        event.put("sessionId", "session");
        event.put("channel", "REMOTE");
        event.put("utteranceSequence", 1);
        event.put("sourceText", "東京");
        event.put("detectedLanguage", "ja");
        event.put("languageConfidence", 0.95);
        event.put("lockedLanguage", "ja");
        event.set("sourceReadingTokens", mapper.createArrayNode().addObject()
                .put("surface", "wrong").put("reading", "wrong"));
        return event;
    }

    private ArrayNode localReadings() {
        ArrayNode tokens = mapper.createArrayNode();
        tokens.addObject().put("surface", "東京").put("reading", "とうきょう");
        return tokens;
    }

    private VoicePolicyProperties policy() {
        return new VoicePolicyProperties(3_600_000, 7_200_000, 6_400, 3_000, 100, 30,
                3_000, 10, 3, 25_000, 90, 300, 250, 10_000, 0.8, 0.85, 3);
    }
}
