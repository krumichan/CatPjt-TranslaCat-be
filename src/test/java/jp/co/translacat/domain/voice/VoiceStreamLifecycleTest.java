package jp.co.translacat.domain.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.EntityManager;
import jp.co.translacat.domain.voice.config.VoicePolicyProperties;
import jp.co.translacat.domain.voice.dto.response.VoiceSessionResponseDto;
import jp.co.translacat.domain.voice.enums.*;
import jp.co.translacat.domain.voice.facade.VoiceSessionFacade;
import jp.co.translacat.domain.voice.model.VoiceStreamContext;
import jp.co.translacat.domain.voice.entity.VoiceSession;
import jp.co.translacat.domain.voice.repository.*;
import jp.co.translacat.domain.voice.service.*;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.domain.voice.websocket.handler.VoicePublicWebSocketHandler;
import jp.co.translacat.domain.voice.websocket.service.*;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.CloseStatus;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoiceStreamLifecycleTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final VoiceStreamContext context = new VoiceStreamContext(1L, "audit", VoiceChannel.REMOTE,
            "connection", VoiceMode.MEDIA, VoiceSourceLanguageMode.AUTO, null, null, "ko", "{}", 0);

    private VoicePolicyProperties policy(int completeMs) {
        return new VoicePolicyProperties(3600000, 7200000, 6400, 3000, 100, 30,
                3000, 10, 3, completeMs, 90, 300, 250, 10000, 0.8, 0.85, 3);
    }

    private VoiceRelayConnection relay(WebSocketSession publicSocket) {
        return new VoiceRelayConnection(context, publicSocket, policy(25000), error -> {
            throw new AssertionError(error);
        });
    }

    @Test
    void noSpeechDoesNotPersistTranscriptOrChargeUsage() {
        // 준비: 무음 이벤트와 저장/사용량 서비스 경계를 구성한다.
        var finals = mock(VoiceFinalResultCommandService.class);
        var streams = mock(VoiceStreamCommandService.class);
        var usage = mock(VoiceUsageQueryService.class);
        var processor = new VoiceAiEventProcessor(mapper, finals, streams, usage);
        var relay = relay(mock(WebSocketSession.class));

        // 실행
        var event = processor.process(relay, """
            {"type":"NO_SPEECH","eventId":"silence-1","sessionId":"audit","channel":"REMOTE",
             "utteranceSequence":1,"reason":"SILENCE"}
            """);

        // 검증
        assertThat(event.type()).isEqualTo(VoiceAiEventType.NO_SPEECH);
        assertThat(event.streamClosed()).isFalse();
        verifyNoInteractions(finals, streams, usage);
        assertThat(relay.acceptPartial(1, 1)).isFalse();
    }

    @Test
    void channelMismatchIsRejectedBeforePersistence() {
        // 준비
        var finals = mock(VoiceFinalResultCommandService.class);
        var streams = mock(VoiceStreamCommandService.class);
        var usage = mock(VoiceUsageQueryService.class);
        var processor = new VoiceAiEventProcessor(mapper, finals, streams, usage);

        // 실행 및 검증
        assertThatThrownBy(() -> processor.process(relay(mock(WebSocketSession.class)), """
            {"type":"NO_SPEECH","eventId":"silence-1","sessionId":"audit","channel":"SELF"}
            """)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(VoiceErrorCode.AI_EVENT_SCOPE_MISMATCH));
        verifyNoInteractions(finals, streams, usage);
    }

    @Test
    void silencePcmIsRelayedWithoutBackendSpeechDetection() {
        // 준비: AI 모델 대신 소켓만 mock 하며 무음 PCM을 넣는다.
        var ai = mock(VoiceAiStreamClient.Connection.class);
        when(ai.sendBinary(any())).thenReturn(CompletableFuture.completedFuture(null));
        var relay = relay(mock(WebSocketSession.class));
        relay.attachAi(ai);
        relay.markReady();

        // 실행
        relay.acceptAudio(new byte[3200]);

        // 검증: 무음 판정은 BE가 아닌 AI 책임이다.
        verify(ai).sendBinary(argThat(bytes -> bytes.length == 3200));
        verifyNoMoreInteractions(ai);
    }

    @Test
    void closeBeforeAiAttachClosesSocketAndDiscardsBufferedAudio() throws Exception {
        // 준비: 연결 준비 중 오디오가 큐에 들어온 상황이다.
        var socket = mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);
        var relay = relay(socket);
        relay.acceptAudio(new byte[3200]);

        // 실행: 사용자가 즉시 종료하고 그 뒤 AI 연결이 도착한다.
        var closed = relay.closeGracefully("SESSION_COMPLETE");
        var ai = mock(VoiceAiStreamClient.Connection.class);
        when(ai.sendBinary(any())).thenReturn(CompletableFuture.completedFuture(null));
        relay.attachAi(ai);
        relay.markReady();

        // 검증: 늦은 연결과 큐를 모두 취소하고 추가 오디오는 보내지 않는다.
        assertThat(closed).isCompleted();
        assertThat(relay.isCloseRequested()).isTrue();
        assertThat(relay.isTerminated()).isTrue();
        assertThat(relay.bufferedAudioMs()).isZero();
        verify(ai, never()).sendBinary(any());
        verify(ai, never()).sendText(any());
        verify(ai).abort();
        verify(socket).close(CloseStatus.NORMAL);
    }

    @Test
    void completionTimeoutFailsSessionAndForcesEveryChannelClosed() {
        // 준비: 마지막 STT 또는 번역이 제한 시간 안에 끝나지 않은 상태다.
        var commands = mock(VoiceSessionCommandService.class);
        var registry = new VoiceConnectionRegistry();
        var closeResult = new CompletableFuture<Void>();
        var remote = mock(VoiceConnectionRegistry.GracefulConnection.class);
        var self = mock(VoiceConnectionRegistry.GracefulConnection.class);
        when(remote.closeGracefully(any())).thenReturn(closeResult);
        when(self.closeGracefully(any())).thenReturn(CompletableFuture.completedFuture(null));
        registry.register("audit", "remote", remote);
        registry.register("audit", "self", self);
        when(commands.startCompleting(1L, "audit")).thenReturn(true);
        var facade = new VoiceSessionFacade(commands, mock(VoiceSessionQueryService.class), null, null,
                null, null, registry, policy(10));

        // 실행 및 검증: 한쪽 채널만 지연되어도 회의의 두 연결을 모두 종료한다.
        assertThatThrownBy(() -> facade.complete(1L, "audit"))
                .isInstanceOfSatisfying(BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(VoiceErrorCode.COMPLETE_TIMEOUT));
        verify(commands).fail(1L, "audit");
        verify(commands, never()).finishCompletion(any(), any());
        verify(remote).forceClose(CloseStatus.SERVER_ERROR);
        verify(self).forceClose(CloseStatus.SERVER_ERROR);
        assertThat(registry.closeSession("audit", "SESSION_COMPLETE")).isCompleted();
    }

    @Test
    void completionPreservesAsyncDomainFailureCodeAndCleansTheConnection() {
        // 준비: timeout 전에 AI 연결이 실패한 종료 요청이다.
        var commands = mock(VoiceSessionCommandService.class);
        var registry = new VoiceConnectionRegistry();
        var connection = mock(VoiceConnectionRegistry.GracefulConnection.class);
        when(connection.closeGracefully(any())).thenReturn(CompletableFuture.failedFuture(
                new CompletionException(new BusinessException("AI closed", VoiceErrorCode.AI_CONNECTION_CLOSED))));
        registry.register("audit", "connection", connection);
        when(commands.startCompleting(1L, "audit")).thenReturn(true);
        var facade = new VoiceSessionFacade(commands, mock(VoiceSessionQueryService.class), null, null,
                null, null, registry, policy(25000));

        // 실행 및 검증
        assertThatThrownBy(() -> facade.complete(1L, "audit"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(VoiceErrorCode.AI_CONNECTION_CLOSED));
        verify(commands).fail(1L, "audit");
        verify(connection).forceClose(CloseStatus.SERVER_ERROR);
    }

    @Test
    void completionRejectsDurableChannelFailureAfterRelayWasAlreadyRemoved() {
        // 준비: unmount 후 complete 요청이 올 때 relay registry는 비어 있고 DB에는 ERROR가 남아 있다.
        var commands = mock(VoiceSessionCommandService.class);
        when(commands.startCompleting(1L, "audit")).thenReturn(true);
        when(commands.finishCompletion(1L, "audit")).thenReturn(false);
        var facade = new VoiceSessionFacade(commands, mock(VoiceSessionQueryService.class), null, null,
                null, null, new VoiceConnectionRegistry(), policy(25000));

        // 실행 및 검증: 사라진 closeFuture를 완료 성공으로 취급하지 않는다.
        assertThatThrownBy(() -> facade.complete(1L, "audit"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(VoiceErrorCode.AI_CONNECTION_CLOSED));
        verify(commands).finishCompletion(1L, "audit");
    }

    @Test
    void repeatedCompletionStillRunsTerminalCleanupWithoutFlushingAgain() {
        // 준비: 이미 완료된 과거 세션의 정리 요청이다.
        var commands = mock(VoiceSessionCommandService.class);
        var registry = mock(VoiceConnectionRegistry.class);
        when(commands.startCompleting(1L, "audit")).thenReturn(false);
        when(commands.finishCompletion(1L, "audit")).thenReturn(true);
        var facade = new VoiceSessionFacade(commands, mock(VoiceSessionQueryService.class), null, null,
                null, null, registry, policy(25000));

        // 실행
        facade.complete(1L, "audit");

        // 검증: 소켓을 다시 열거나 flush하지 않고 DB terminal 정리를 수행한다.
        verify(commands).finishCompletion(1L, "audit");
        verifyNoInteractions(registry);
    }

    @Test
    void repeatedFailedCompletionReturnsFailedStateWithoutReportingSuccessOrInvalidState() {
        // 준비: 이전 종료에서 실패가 확정된 세션을 화면 정리가 다시 종료한다.
        var commands = mock(VoiceSessionCommandService.class);
        var queries = mock(VoiceSessionQueryService.class);
        var registry = mock(VoiceConnectionRegistry.class);
        when(commands.startCompleting(1L, "audit")).thenReturn(false);
        when(commands.finishCompletion(1L, "audit")).thenReturn(false);
        var failedSession = new VoiceSessionResponseDto("audit", VoiceMode.MEDIA,
                VoiceSourceLanguageMode.AUTO, "ko", true, VoiceSessionStatus.FAILED,
                "Voice", 900, null, null, null, List.of());
        when(queries.get(1L, "audit")).thenReturn(failedSession);
        var facade = new VoiceSessionFacade(commands, queries, null, null,
                null, null, registry, policy(25000));

        // 실행
        var response = facade.complete(1L, "audit");

        // 검증: 정리는 멱등적으로 끝나지만 소비자에게 전달되는 결과는 명시적으로 FAILED다.
        assertThat(response.status()).isEqualTo(VoiceSessionStatus.FAILED);
        verify(commands).finishCompletion(1L, "audit");
        verify(registry).forceCloseSession("audit");
        verify(registry, never()).closeSession(any(), any());
    }

    @Test
    void speechFailureDiagnosticOmitsTranscriptAndProviderMessage() {
        // 준비: DB 행이 없는 STT 실패도 세션/발화 식별자로 추적하되 내용을 기록하지 않는다.
        var processor = new VoiceAiEventProcessor(mapper, mock(VoiceFinalResultCommandService.class),
                mock(VoiceStreamCommandService.class), mock(VoiceUsageQueryService.class));
        var logger = (Logger) LoggerFactory.getLogger(VoiceAiEventProcessor.class);
        var captured = new ListAppender<ILoggingEvent>();
        captured.start();
        logger.addAppender(captured);

        try {
            // 실행: 공급자 메시지와 회의 원문은 로그에 포함되면 안 된다.
            processor.process(relay(mock(WebSocketSession.class)), """
                {"type":"VOICE_PIPELINE_FAILED","eventId":"timeout-1","sessionId":"audit","channel":"REMOTE",
                 "utteranceSequence":3,"sourceText":"private meeting text",
                 "error":{"code":"VOICE_STT_TIMEOUT","stage":"STT","retryable":true,
                          "message":"private provider detail"}}
                """);

            // 검증
            assertThat(captured.list).hasSize(1);
            assertThat(captured.list.getFirst().getFormattedMessage())
                    .contains("session=audit", "channel=REMOTE", "sequence=3", "code=VOICE_STT_TIMEOUT", "stage=STT")
                    .doesNotContain("private meeting text", "private provider detail");
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }

    @Test
    void persistenceFailureIsNonRetryableBackendErrorOnSocketAndDrain() throws Exception {
        // 준비: 번역 저장 오류가 AI 연결 오류로 잘못 분류되지 않아야 한다.
        var socket = mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);
        var relay = relay(socket);
        var handler = new VoicePublicWebSocketHandler(mock(VoiceStreamCommandService.class),
                mock(VoiceAiStreamClient.class), mock(VoiceAiEventProcessor.class),
                new VoiceConnectionRegistry(), policy(25000), mapper);
        ReflectionTestUtils.setField(handler, "connections", Map.of("connection", relay));

        // 실행
        ReflectionTestUtils.invokeMethod(handler, "onAsyncError", "connection",
                new CompletionException(new DataIntegrityViolationException("test persistence failure")));

        // 검증: fatal STREAM 오류는 WS와 HTTP 종료 future에 같은 코드로 전달된다.
        var event = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(socket).sendMessage(event.capture());
        var error = mapper.readTree(event.getValue().getPayload().toString()).path("error");
        assertThat(error.path("code").asText()).isEqualTo(VoiceErrorCode.BE_INTERNAL_ERROR);
        assertThat(error.path("stage").asText()).isEqualTo("STREAM");
        assertThat(error.path("retryable").asBoolean()).isFalse();
        assertThatThrownBy(() -> relay.closeGracefully("SESSION_COMPLETE").join())
                .hasCauseInstanceOf(BusinessException.class)
                .satisfies(failure -> assertThat(((BusinessException) failure.getCause()).getErrorCode())
                        .isEqualTo(VoiceErrorCode.BE_INTERNAL_ERROR));
    }

    @Test
    void interruptedDrainPersistsChannelErrorBeforeRemovingRegistryEntry() {
        // 준비: 화면 이탈의 graceful 요청 뒤 ACK 없이 public transport가 닫혔다.
        var socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("connection");
        var ai = mock(VoiceAiStreamClient.Connection.class);
        when(ai.sendText(any())).thenReturn(CompletableFuture.completedFuture(null));
        var relay = relay(socket);
        relay.attachAi(ai);
        var drained = relay.closeGracefully("CLIENT_LEAVE");
        var commands = mock(VoiceStreamCommandService.class);
        var registry = mock(VoiceConnectionRegistry.class);
        var handler = new VoicePublicWebSocketHandler(commands, mock(VoiceAiStreamClient.class),
                mock(VoiceAiEventProcessor.class), registry, policy(25000), mapper);
        ReflectionTestUtils.setField(handler, "connections", new HashMap<>(Map.of("connection", relay)));

        // 실행
        handler.afterConnectionClosed(socket, CloseStatus.NORMAL);

        // 검증: complete HTTP가 registry를 놓쳐도 ERROR가 이미 저장되어 있어야 한다.
        var order = inOrder(commands, registry);
        order.verify(commands).markError(context);
        order.verify(registry).remove("audit", "connection");
        verify(commands, never()).markDisconnected(any());
        assertThat(drained).isCompletedExceptionally();
    }

    @Test
    void specificFailureCodeSurvivesNestedAsyncWrappers() throws Exception {
        // 준비: AI 준비 시간 초과를 발생시킨다.
        var socket = mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);
        var relay = relay(socket);
        var handler = new VoicePublicWebSocketHandler(mock(VoiceStreamCommandService.class),
                mock(VoiceAiStreamClient.class), mock(VoiceAiEventProcessor.class),
                new VoiceConnectionRegistry(), policy(25000), mapper);
        ReflectionTestUtils.setField(handler, "connections", Map.of("connection", relay));

        // 실행
        ReflectionTestUtils.invokeMethod(handler, "onAsyncError", "connection",
                new CompletionException(new CompletionException(
                        new BusinessException("ready timeout", VoiceErrorCode.AI_READY_TIMEOUT))));

        // 검증: 구체적인 timeout 코드와 재시도 여부가 사용자 이벤트에 유지된다.
        var event = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(socket).sendMessage(event.capture());
        var json = mapper.readTree(event.getValue().getPayload().toString());
        assertThat(json.path("error").path("code").asText()).isEqualTo(VoiceErrorCode.AI_READY_TIMEOUT);
        assertThat(json.path("error").path("retryable").asBoolean()).isTrue();
        assertThatThrownBy(() -> relay.closeGracefully("SESSION_COMPLETE").join())
                .hasCauseInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error.getCause()).getErrorCode())
                        .isEqualTo(VoiceErrorCode.AI_READY_TIMEOUT));
    }

    @Test
    void mediaAndMeetingStreamOpenPreserveManualAndAutomaticLanguages() throws Exception {
        // 준비
        var client = new VoiceAiStreamClient(mapper, policy(25000), "http://localhost:1", "test-key");
        var manual = new VoiceStreamContext(1L, "audit", VoiceChannel.SELF, "manual", VoiceMode.MEETING,
                VoiceSourceLanguageMode.MANUAL, "ja", "ja", "ko", "{}", 0);

        // 실행
        String mediaJson = ReflectionTestUtils.invokeMethod(client, "buildStreamOpen", context);
        String meetingJson = ReflectionTestUtils.invokeMethod(client, "buildStreamOpen", manual);
        var media = mapper.readTree(mediaJson);
        var meeting = mapper.readTree(meetingJson);

        // 검증
        assertThat(media.path("mode").asText()).isEqualTo("MEDIA");
        assertThat(media.path("sourceLanguageMode").asText()).isEqualTo("AUTO");
        assertThat(media.path("targetLanguage").asText()).isEqualTo("ko");
        assertThat(media.path("audioFormat").path("sampleRate").asInt()).isEqualTo(16000);
        assertThat(meeting.path("mode").asText()).isEqualTo("MEETING");
        assertThat(meeting.path("sourceLanguageMode").asText()).isEqualTo("MANUAL");
        assertThat(meeting.path("manualSourceLanguage").asText()).isEqualTo("ja");
    }

    @Test
    void relayRejectsOversizedFrameAndCapsPreReadyBuffer() {
        // 준비
        var relay = relay(mock(WebSocketSession.class));

        // 실행 및 검증: PCM 3초를 넘기는 준비 큐와 6400-byte 초과 frame을 거절한다.
        assertThatThrownBy(() -> relay.acceptAudio(new byte[6401]))
                .isInstanceOf(BusinessException.class);
        for (int i = 0; i < 30; i++) {
            relay.acceptAudio(new byte[3200]);
        }
        assertThat(relay.bufferedAudioMs()).isEqualTo(3000);
        assertThatThrownBy(() -> relay.acceptAudio(new byte[3200]))
                .isInstanceOf(VoiceRelayConnection.BackpressureException.class);
    }

    @Test
    void failurePathDeletesTranscriptWhenSavingIsDisabled() {
        // 준비: 저장하지 않기를 선택한 세션에 이미 일부 transcript가 저장되어 있다.
        var session = mock(VoiceSession.class);
        when(session.isSaveTranscript()).thenReturn(false);
        var sessions = mock(VoiceSessionRepository.class);
        var segments = mock(VoiceSegmentRepository.class);
        when(sessions.findByIdAndUser_Id("audit", 1L)).thenReturn(Optional.of(session));
        var commands = new VoiceSessionCommandService(mock(EntityManager.class), sessions, null, segments,
                null, policy(25000), mapper);

        // 실행
        commands.fail(1L, "audit");

        // 검증: 실패 경로도 정상 완료와 동일하게 미보관 세그먼트를 삭제한다.
        verify(session).fail();
        verify(segments).deleteBySession_Id("audit");
        assertThat(VoiceSessionStatus.FAILED.isActiveLike()).isFalse();
    }

    @Test
    void failureKeepsSavedTranscriptAndMakesUnfinishedTranslationRetryable() {
        // 준비: 원문 보관을 선택했지만 마지막 번역이 완료되지 못했다.
        var session = mock(VoiceSession.class);
        when(session.isSaveTranscript()).thenReturn(true);
        var sessions = mock(VoiceSessionRepository.class);
        var segments = mock(VoiceSegmentRepository.class);
        var pending = mock(jp.co.translacat.domain.voice.entity.VoiceSegment.class);
        when(sessions.findByIdAndUser_Id("audit", 1L)).thenReturn(Optional.of(session));
        when(segments.findBySession_IdAndStatus("audit", VoiceSegmentStatus.TRANSLATING))
                .thenReturn(List.of(pending));
        var commands = new VoiceSessionCommandService(mock(EntityManager.class), sessions, null, segments,
                null, policy(25000), mapper);

        // 실행
        commands.fail(1L, "audit");

        // 검증: 이력은 보존하고 기존 언어 실패를 덮어쓰지 않은 채 진행 중 번역만 실패 처리한다.
        verify(segments, never()).deleteBySession_Id(any());
        verify(pending).markTranslationFailed(VoiceErrorCode.COMPLETE_TIMEOUT);
    }

    @Test
    void successfulCompletionWaitsForBothMeetingChannels() {
        // 준비: 상대 발화가 아직 번역 중이고 본인 채널만 종료한 회의다.
        var commands = mock(VoiceSessionCommandService.class);
        var registry = new VoiceConnectionRegistry();
        var remote = mock(VoiceConnectionRegistry.GracefulConnection.class);
        var self = mock(VoiceConnectionRegistry.GracefulConnection.class);
        var remoteClosed = new CompletableFuture<Void>();
        when(remote.closeGracefully(any())).thenReturn(remoteClosed);
        when(self.closeGracefully(any())).thenReturn(CompletableFuture.completedFuture(null));
        registry.register("audit", "remote", remote);
        registry.register("audit", "self", self);
        when(commands.startCompleting(1L, "audit")).thenReturn(true);
        when(commands.finishCompletion(1L, "audit")).thenReturn(true);
        var facade = new VoiceSessionFacade(commands, mock(VoiceSessionQueryService.class), null, null,
                null, null, registry, policy(25000));

        // 실행: 양 채널 drain 확인 후에만 DB 완료를 확정한다.
        when(remote.closeGracefully("SESSION_COMPLETE")).thenAnswer(invocation -> {
            verify(commands, never()).finishCompletion(any(), any());
            remoteClosed.complete(null);
            return remoteClosed;
        });
        facade.complete(1L, "audit");

        // 검증
        verify(remote).closeGracefully("SESSION_COMPLETE");
        verify(self).closeGracefully("SESSION_COMPLETE");
        verify(commands).finishCompletion(1L, "audit");
        verify(commands, never()).fail(any(), any());
        verify(remote, never()).forceClose(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1000, 1011})
    void forceCloseCancelsPendingGracefulCompletionAndIgnoresLateReady(int closeCode) throws Exception {
        // 준비: AI close acknowledgement를 기다리는 연결이다.
        var socket = mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);
        var ai = mock(VoiceAiStreamClient.Connection.class);
        when(ai.sendText(any())).thenReturn(CompletableFuture.completedFuture(null));
        var relay = relay(socket);
        relay.attachAi(ai);
        var closed = relay.closeGracefully("SESSION_COMPLETE");
        var status = new CloseStatus(closeCode);

        // 실행
        relay.forceClose(status);
        relay.forceClose(status);
        relay.markReady();

        // 검증: timeout은 성공으로 보고하지 않으며 중복 종료/늦은 ready가 재활성화하지 않는다.
        assertThat(closed).isCompletedExceptionally();
        assertThat(relay.isReady()).isFalse();
        verify(ai).abort();
        verify(socket).close(status);
    }

    @Test
    void errorWhileSendingErrorClosesOnceWithoutRecursiveFailure() throws Exception {
        // 준비: public 소켓 쓰기가 실패하면 동일 handler로 transport 오류가 다시 들어온다.
        var socket = mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);
        var handler = new VoicePublicWebSocketHandler(mock(VoiceStreamCommandService.class),
                mock(VoiceAiStreamClient.class), mock(VoiceAiEventProcessor.class),
                new VoiceConnectionRegistry(), policy(25000), mapper);
        var relay = new VoiceRelayConnection(context, socket, policy(25000), error ->
                ReflectionTestUtils.invokeMethod(handler, "onAsyncError", "connection", error));
        ReflectionTestUtils.setField(handler, "connections", Map.of("connection", relay));
        doThrow(new IOException("closed transport")).when(socket).sendMessage(any());

        // 실행
        ReflectionTestUtils.invokeMethod(handler, "onAsyncError", "connection",
                new BusinessException("ready timeout", VoiceErrorCode.AI_READY_TIMEOUT));

        // 검증: 오류 이벤트 전송은 한 번만 시도하고 소켓 종료까지 진행한다.
        verify(socket).sendMessage(any());
        verify(socket).close(CloseStatus.SERVER_ERROR);
        assertThat(relay.isTerminated()).isTrue();
    }

    @Test
    void aiTransportCloseWithoutStreamClosedAcknowledgementFailsDrain() {
        // 준비: close 요청을 보냈지만 최종 이벤트 전달 확인은 받지 못했다.
        var socket = mock(WebSocketSession.class);
        var ai = mock(VoiceAiStreamClient.Connection.class);
        when(ai.sendText(any())).thenReturn(CompletableFuture.completedFuture(null));
        var relay = relay(socket);
        relay.attachAi(ai);
        var closed = relay.closeGracefully("SESSION_COMPLETE");
        var handler = new VoicePublicWebSocketHandler(mock(VoiceStreamCommandService.class),
                mock(VoiceAiStreamClient.class), mock(VoiceAiEventProcessor.class),
                new VoiceConnectionRegistry(), policy(25000), mapper);
        ReflectionTestUtils.setField(handler, "connections", Map.of("connection", relay));

        // 실행
        ReflectionTestUtils.invokeMethod(handler, "onAiClosed", "connection");

        // 검증: 요청만 전송한 상태를 정상 완료로 오인하지 않는다.
        assertThat(closed).isCompletedExceptionally();
        assertThat(relay.isTerminated()).isTrue();
    }

    @Test
    void completedAndTranslationFailureCannotUpdateTerminalSession() throws Exception {
        // 준비: 이미 완료된 보관 세션도 새로운 pipeline 이벤트로 덮어쓰지 않는다.
        var sessions = mock(VoiceSessionRepository.class);
        var segments = mock(VoiceSegmentRepository.class);
        var usage = mock(VoiceUsageLedgerRepository.class);
        var session = mock(VoiceSession.class);
        when(session.getStatus()).thenReturn(VoiceSessionStatus.COMPLETED);
        when(sessions.findOwnedForUpdate("audit", 1L)).thenReturn(Optional.of(session));
        var finals = new VoiceFinalResultCommandService(sessions, segments, usage,
                mock(VoiceStreamCommandService.class), mapper);
        var event = mapper.readTree("""
                {"eventId":"late","sourceText":"The meeting is over",
                 "error":{"stage":"TRANSLATION","code":"VOICE_AI_TRANSLATION_TIMEOUT"}}
                """);

        // 실행 및 검증: 세그먼트 조회/갱신 전에 세션 상태 검사를 끝낸다.
        assertThatThrownBy(() -> finals.persistCompleted(context, event, 1, 0))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> finals.persistTranslationFailure(context, event, 1))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> finals.updateRelayLatency(context, 1L, 0))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(segments, usage);
    }

    @Test
    void failedSessionRejectsLateTranscriptFinal() throws Exception {
        // 준비: 종료 제한으로 FAILED 처리된 세션이다.
        var sessions = mock(VoiceSessionRepository.class);
        var segments = mock(VoiceSegmentRepository.class);
        var usage = mock(VoiceUsageLedgerRepository.class);
        var session = mock(VoiceSession.class);
        when(session.getStatus()).thenReturn(VoiceSessionStatus.FAILED);
        when(sessions.findOwnedForUpdate("audit", 1L)).thenReturn(Optional.of(session));
        var finals = new VoiceFinalResultCommandService(sessions, segments, usage,
                mock(VoiceStreamCommandService.class), mapper);
        var event = mapper.readTree("{\"eventId\":\"late\",\"sourceText\":\"The meeting is over\"}");

        // 실행 및 검증: 뒤늦게 도착한 마지막 문장은 저장되지 않는다.
        assertThatThrownBy(() -> finals.persistTranscriptFinal(context, event, 1))
                .isInstanceOfSatisfying(BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(VoiceErrorCode.LATE_FINAL_EVENT));
        verify(segments, never()).saveAndFlush(any());
        verifyNoInteractions(usage);
    }
}
