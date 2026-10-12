package jp.co.translacat.domain.voice.facade;

import jp.co.translacat.domain.voice.config.VoicePolicyProperties;
import jp.co.translacat.domain.voice.dto.request.VoiceSessionCreateRequestDto;
import jp.co.translacat.domain.voice.dto.request.VoiceSessionUpdateRequestDto;
import jp.co.translacat.domain.voice.dto.response.*;
import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.model.VoiceTranslationRetryContext;
import jp.co.translacat.domain.voice.service.*;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.domain.voice.websocket.service.VoiceConnectionRegistry;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.client.ai.server.voice.VoiceAiTranslationRetryClient;
import jp.co.translacat.infrastructure.client.ai.server.voice.dto.AiVoiceTranslationRetryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class VoiceSessionFacade {

    private final VoiceSessionCommandService sessionCommandService;
    private final VoiceSessionQueryService sessionQueryService;
    private final VoiceTranslationRetryQueryService retryQueryService;
    private final VoiceTranslationRetryCommandService retryCommandService;
    private final VoiceAiTranslationRetryClient aiTranslationRetryClient;
    private final VoiceWebSocketTicketService webSocketTicketService;
    private final VoiceConnectionRegistry connectionRegistry;
    private final VoicePolicyProperties policy;

    public VoiceSessionResponseDto create(
            Long userId,
            VoiceSessionCreateRequestDto request
    ) {
        String sessionId = sessionCommandService.create(
                userId,
                request
        );

        return sessionQueryService.get(userId, sessionId);
    }

    public VoiceSessionResponseDto get(
            Long userId,
            String sessionId
    ) {
        return sessionQueryService.get(userId, sessionId);
    }

    public VoiceSessionListResponseDto listHistory(
            Long userId,
            LocalDateTime cursor,
            Integer size
    ) {
        return sessionQueryService.listHistory(
                userId,
                cursor,
                size
        );
    }

    public VoiceSegmentListResponseDto listSegments(
            Long userId,
            String sessionId,
            Long cursor,
            Integer size
    ) {
        return sessionQueryService.listSegments(
                userId,
                sessionId,
                cursor,
                size
        );
    }

    public VoiceSessionResponseDto update(
            Long userId,
            String sessionId,
            VoiceSessionUpdateRequestDto request
    ) {
        sessionCommandService.update(
                userId,
                sessionId,
                request
        );

        return sessionQueryService.get(userId, sessionId);
    }

    public VoiceSessionResponseDto complete(
            Long userId,
            String sessionId
    ) {
        boolean shouldFlush = sessionCommandService.startCompleting(
                userId,
                sessionId
        );

        if (shouldFlush) {
            flushConnections(userId, sessionId);
        }

        // 이미 완료된 세션도 이전 연결/버전이 남긴 미완료 문장은 정리할 수 있어야 한다.
        boolean completed = sessionCommandService.finishCompletion(userId, sessionId);
        if (!completed) {
            connectionRegistry.forceCloseSession(sessionId);
            throw new BusinessException(
                    "A Voice channel closed without completing its final results.",
                    VoiceErrorCode.AI_CONNECTION_CLOSED
            );
        }

        return sessionQueryService.get(userId, sessionId);
    }

    public void delete(
            Long userId,
            String sessionId
    ) {
        sessionCommandService.delete(userId, sessionId);
    }

    public VoiceWebSocketTicketResponseDto issueWebSocketTicket(
            Long userId,
            String sessionId,
            VoiceChannel channel
    ) {
        sessionQueryService.validateStreamAccess(
                userId,
                sessionId,
                channel
        );

        return new VoiceWebSocketTicketResponseDto(
                webSocketTicketService.issue(
                        userId,
                        sessionId,
                        channel
                ),
                policy.getWebsocketTicketTtlSeconds()
        );
    }

    public VoiceTranslationRetryResponseDto retryTranslation(
            Long userId,
            String sessionId,
            Long segmentId
    ) {
        VoiceTranslationRetryContext context = retryQueryService.prepare(
                userId,
                sessionId,
                segmentId
        );
        AiVoiceTranslationRetryResponse aiResponse =
                aiTranslationRetryClient.retry(
                        sessionId,
                        segmentId,
                        context
                );
        VoiceSegmentResponseDto segment = retryCommandService.apply(
                userId,
                sessionId,
                segmentId,
                context,
                aiResponse
        );

        return new VoiceTranslationRetryResponseDto(segment);
    }

    private void flushConnections(
            Long userId,
            String sessionId
    ) {
        try {
            connectionRegistry.closeSession(
                            sessionId,
                            "SESSION_COMPLETE"
                    )
                    .get(
                            policy.getSessionCompleteTimeoutMs(),
                            TimeUnit.MILLISECONDS
                    );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failCompletion(userId, sessionId, e);
        } catch (ExecutionException | TimeoutException e) {
            failCompletion(userId, sessionId, e);
        } catch (RuntimeException e) {
            failCompletion(userId, sessionId, e);
        }
    }

    private void failCompletion(
            Long userId,
            String sessionId,
            Throwable failure
    ) {
        // DB의 terminal 상태/미보관 삭제를 먼저 확정해 늦은 Final 쓰기와 직렬화한다.
        // DB 정리에 실패해도 소켓과 준비 중인 AI 연결은 반드시 종료한다.
        try {
            sessionCommandService.fail(userId, sessionId);
        } finally {
            connectionRegistry.forceCloseSession(sessionId);
        }
        // 소켓/도메인 실패를 timeout으로 바꾸지 않고 HTTP 종료 응답에도 원래 코드를 보존한다.
        Throwable cause = failure;
        while ((cause instanceof ExecutionException || cause instanceof CompletionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof BusinessException business
                && business.getErrorCode() != null
                && !business.getErrorCode().isBlank()) {
            throw business;
        }

        boolean timedOut = cause instanceof TimeoutException || cause instanceof InterruptedException;
        throw new BusinessException(
                timedOut ? "Timed out while flushing Voice streams." : "Failed while closing Voice streams.",
                timedOut ? VoiceErrorCode.COMPLETE_TIMEOUT : VoiceErrorCode.BE_INTERNAL_ERROR
        );
    }
}
