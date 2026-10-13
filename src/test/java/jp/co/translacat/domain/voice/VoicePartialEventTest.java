package jp.co.translacat.domain.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jp.co.translacat.domain.voice.config.VoicePolicyProperties;
import jp.co.translacat.domain.voice.entity.VoiceSegment;
import jp.co.translacat.domain.voice.enums.*;
import jp.co.translacat.domain.voice.model.VoiceStreamContext;
import jp.co.translacat.domain.voice.service.VoiceFinalResultCommandService;
import jp.co.translacat.domain.voice.service.VoiceStreamCommandService;
import jp.co.translacat.domain.voice.service.VoiceUsageQueryService;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.domain.voice.websocket.handler.VoicePublicWebSocketHandler;
import jp.co.translacat.domain.voice.websocket.service.*;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VoicePartialEventTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final VoiceStreamContext context = new VoiceStreamContext(1L, "preview", VoiceChannel.REMOTE,
            "connection", VoiceMode.MEDIA, VoiceSourceLanguageMode.AUTO, null, null, "ko", "{}", 7);
    private final VoicePolicyProperties policy = new VoicePolicyProperties(3600000, 7200000, 6400, 3000,
            100, 30, 3000, 10, 3, 25000, 90, 300, 250, 10000, 0.8, 0.85, 3);
    private final VoiceFinalResultCommandService finals = mock(VoiceFinalResultCommandService.class);
    private final VoiceStreamCommandService streams = mock(VoiceStreamCommandService.class);
    private final VoiceUsageQueryService usage = mock(VoiceUsageQueryService.class);
    private final VoiceAiEventProcessor processor = new VoiceAiEventProcessor(mapper, finals, streams, usage);
    private final WebSocketSession socket = mock(WebSocketSession.class);
    private final VoiceRelayConnection relay = new VoiceRelayConnection(context, socket, policy,
            error -> { throw new AssertionError(error); });

    @Test
    void sourceOnlyPreviewKeepsExistingContractWithoutPersistence() throws Exception {
        // 준비: 이전 AI의 원문 전용 이벤트도 같은 계약으로 허용한다.
        ObjectNode event = partial(1);

        // 실행
        var result = processor.process(relay, event.toString());
        var publicEvent = mapper.readTree(result.publicJson());

        // 검증: reconnect offset을 적용하되 preview를 저장하거나 과금하지 않는다.
        assertThat(publicEvent.path("utteranceSequence").asLong()).isEqualTo(8);
        assertThat(publicEvent.path("utteranceKey").asText()).isEqualTo("REMOTE-8");
        assertThat(publicEvent.path("sourceText").asText()).isEqualTo("The meeting starts");
        assertThat(publicEvent.has("translatedText")).isFalse();
        verifyNoInteractions(finals, streams, usage);
    }

    @Test
    void translationPreviewRelaysFullSnapshotsAndCanBeCleared() throws Exception {
        // 준비: 원문과 번역은 하나의 revision으로 갱신하며 번역은 null로 지울 수 있다.
        ObjectNode first = partial(1).put("translatedText", "회의가 시작됩니다").put("targetLanguage", "ko");
        ObjectNode second = partial(2).put("sourceText", "The meeting starts tomorrow").putNull("translatedText");

        // 실행
        var translated = mapper.readTree(processor.process(relay, first.toString()).publicJson());
        var cleared = mapper.readTree(processor.process(relay, second.toString()).publicJson());

        // 검증
        assertThat(translated.path("translatedText").asText()).isEqualTo("회의가 시작됩니다");
        assertThat(translated.path("targetLanguage").asText()).isEqualTo("ko");
        assertThat(cleared.path("translatedText").isNull()).isTrue();
        verifyNoInteractions(finals, streams, usage);
    }

    @Test
    void olderAndDuplicateRevisionsCannotReplaceNewerPreview() {
        // 준비 및 실행: 네트워크 중복/역순 전달을 모사한다.
        var current = processor.process(relay, partial(3).toString());
        var stale = processor.process(relay, partial(2).toString());
        var duplicate = processor.process(relay, partial(3).toString());
        var newest = processor.process(relay, partial(4).toString());

        // 검증
        assertThat(current.suppressed()).isFalse();
        assertThat(stale.suppressed()).isTrue();
        assertThat(duplicate.suppressed()).isTrue();
        assertThat(newest.suppressed()).isFalse();
        verifyNoInteractions(finals, streams, usage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "0", "-1", "1.5", "1.0", "\"2\"", "true", "9007199254740992", "9223372036854775808"})
    void malformedRevisionIsRejectedWithoutPersistence(String revision) throws Exception {
        // 준비
        ObjectNode event = partial(1);
        event.set("revision", mapper.readTree(revision));

        // 실행 및 검증
        assertInvalid(event);
        verifyNoInteractions(finals, streams, usage);
    }

    @Test
    void safeIntegerRevisionDoesNotOverflowJavaInteger() {
        // 준비
        ObjectNode event = partial(9_007_199_254_740_991L);

        // 실행 및 검증
        assertThat(processor.process(relay, event.toString()).suppressed()).isFalse();
        assertThat(processor.process(relay, partial(Integer.MAX_VALUE).toString()).suppressed()).isTrue();
        verifyNoInteractions(finals, streams, usage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "1.0", "\"2\"", "true", "9007199254740992", "9223372036854775808"})
    void malformedSequenceCannotAliasAnotherUtterance(String sequence) throws Exception {
        // 준비: 소수/문자열의 암묵적 정수 변환이나 JS 정밀도 손실을 허용하지 않는다.
        ObjectNode event = partial(1);
        event.set("utteranceSequence", mapper.readTree(sequence));

        // 실행 및 검증
        assertInvalid(event);
        verifyNoInteractions(finals, streams, usage);
    }

    @Test
    void reconnectOffsetCannotExceedSafeSequenceBoundary() throws Exception {
        // 준비: 로컬 번호는 안전해도 reconnect offset을 합친 최종 번호가 넘칠 수 있다.
        ObjectNode lastSafe = partial(1).put("utteranceSequence", 9_007_199_254_740_984L);
        ObjectNode unsafe = partial(1).put("utteranceSequence", 9_007_199_254_740_985L);

        // 실행
        var accepted = mapper.readTree(processor.process(relay, lastSafe.toString()).publicJson());

        // 검증
        assertThat(accepted.path("utteranceSequence").asLong()).isEqualTo(9_007_199_254_740_991L);
        assertInvalid(unsafe);
        verifyNoInteractions(finals, streams, usage);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, 9_007_199_254_740_991L, Long.MAX_VALUE})
    void invalidStoredOffsetCannotOverflowOrReuseAnIdentity(long offset) {
        // 준비
        var invalidContext = new VoiceStreamContext(1L, "preview", VoiceChannel.REMOTE,
                "connection", VoiceMode.MEDIA, VoiceSourceLanguageMode.AUTO, null, null, "ko", "{}", offset);
        var invalidRelay = new VoiceRelayConnection(invalidContext, socket, policy,
                error -> { throw new AssertionError(error); });

        // 실행 및 검증
        assertThatThrownBy(() -> invalidRelay.globalSequence(1))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(VoiceErrorCode.INVALID_EVENT_SCHEMA));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sourceText", "translatedText"})
    void previewTextIsBounded(String field) {
        // 준비
        ObjectNode event = partial(1).put("targetLanguage", "ko").put(field, "あ".repeat(10_001));

        // 실행 및 검증
        assertInvalid(event);
        verifyNoInteractions(finals, streams, usage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "42", "\"\"", "\"ja\""})
    void translatedPreviewRequiresSessionTargetLanguage(String targetLanguage) throws Exception {
        // 준비
        ObjectNode event = partial(1).put("translatedText", "회의가 시작됩니다");
        event.set("targetLanguage", mapper.readTree(targetLanguage));

        // 실행 및 검증
        assertInvalid(event);
        verifyNoInteractions(finals, streams, usage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"42", "true", "\"\"", "\"   \""})
    void malformedTranslatedTextIsRejected(String translatedText) throws Exception {
        // 준비
        ObjectNode event = partial(1).put("targetLanguage", "ko");
        event.set("translatedText", mapper.readTree(translatedText));

        // 실행 및 검증
        assertInvalid(event);
        verifyNoInteractions(finals, streams, usage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRANSCRIPT_FINAL", "VOICE_PIPELINE_COMPLETED", "VOICE_PIPELINE_FAILED", "NO_SPEECH"})
    void finalOrTerminalEventsBlockLatePreview(String finalType) {
        // 준비
        VoiceSegment segment = mock(VoiceSegment.class);
        when(segment.getId()).thenReturn(1L);
        when(finals.persistCompleted(any(), any(), anyLong(), anyLong())).thenReturn(segment);
        processor.process(relay, partial(1).toString());

        // 실행: 원문 확정/종료가 도착한 뒤 더 높은 revision이 도착한다.
        processor.process(relay, event(finalType).toString());
        var late = processor.process(relay, partial(2).toString());

        // 검증
        assertThat(late.suppressed()).isTrue();
    }

    @Test
    void stopDrainsFinalResultBeforeStreamClosedAndSuppressesLatePreview() throws Exception {
        // 준비: 종료 요청 후에도 마지막 확정 결과를 전달할 public/AI 연결을 구성한다.
        when(socket.isOpen()).thenReturn(true);
        var ai = mock(VoiceAiStreamClient.Connection.class);
        when(ai.sendText(any())).thenReturn(CompletableFuture.completedFuture(null));
        relay.attachAi(ai);
        var handler = new VoicePublicWebSocketHandler(streams, mock(VoiceAiStreamClient.class), processor,
                new VoiceConnectionRegistry(), policy, mapper);
        ReflectionTestUtils.setField(handler, "connections", Map.of("connection", relay));
        var drained = relay.closeGracefully("SESSION_COMPLETE");

        // 실행: preview → final → 늦은 preview는 drain 완료 전에 처리된다.
        deliver(handler, partial(1));
        deliver(handler, event("TRANSCRIPT_FINAL"));
        deliver(handler, partial(2));

        // 검증: preview는 저장하지 않고 최종 원문만 저장한다. 아직 close ACK를 기다린다.
        verify(finals).persistTranscriptFinal(eq(context), any(), eq(8L));
        verifyNoMoreInteractions(finals);
        verify(socket, times(2)).sendMessage(any(TextMessage.class));
        assertThat(drained.isDone()).isFalse();

        // 실행 및 검증: 명시적인 STREAM_CLOSED 이후에만 drain을 완료한다.
        deliver(handler, event("STREAM_CLOSED"));
        assertThat(drained).isCompleted();
        verify(socket).close(CloseStatus.NORMAL);
        verify(streams).markDisconnected(context);
    }

    private ObjectNode partial(long revision) {
        return event("TRANSCRIPT_PARTIAL").put("revision", revision).put("sourceText", "The meeting starts");
    }

    private ObjectNode event(String type) {
        return mapper.createObjectNode().put("type", type).put("eventId", type + "-event")
                .put("sessionId", "preview").put("channel", "REMOTE").put("utteranceSequence", 1);
    }

    private void assertInvalid(ObjectNode event) {
        assertThatThrownBy(() -> processor.process(relay, event.toString()))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(VoiceErrorCode.INVALID_EVENT_SCHEMA));
    }

    private void deliver(VoicePublicWebSocketHandler handler, ObjectNode event) {
        ReflectionTestUtils.invokeMethod(handler, "onAiText", "connection", event.toString());
    }
}
