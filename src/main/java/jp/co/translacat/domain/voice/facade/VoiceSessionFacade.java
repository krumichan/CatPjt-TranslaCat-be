package jp.co.translacat.domain.voice.facade;

import jp.co.translacat.domain.voice.config.VoicePolicyProperties;
import jp.co.translacat.domain.voice.dto.request.VoiceSessionCreateRequestDto;
import jp.co.translacat.domain.voice.dto.request.VoiceSessionUpdateRequestDto;
import jp.co.translacat.domain.voice.dto.response.VoiceSegmentListResponseDto;
import jp.co.translacat.domain.voice.dto.response.VoiceSegmentResponseDto;
import jp.co.translacat.domain.voice.dto.response.VoiceSessionListResponseDto;
import jp.co.translacat.domain.voice.dto.response.VoiceSessionResponseDto;
import jp.co.translacat.domain.voice.dto.response.VoiceTranslationRetryResponseDto;
import jp.co.translacat.domain.voice.dto.response.VoiceWebSocketTicketResponseDto;
import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.model.VoiceTranslationRetryContext;
import jp.co.translacat.domain.voice.service.VoiceSessionCommandService;
import jp.co.translacat.domain.voice.service.VoiceSessionQueryService;
import jp.co.translacat.domain.voice.service.VoiceTranslationRetryCommandService;
import jp.co.translacat.domain.voice.service.VoiceTranslationRetryQueryService;
import jp.co.translacat.domain.voice.service.VoiceWebSocketTicketService;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.domain.voice.websocket.service.VoiceConnectionRegistry;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.client.ai.server.voice.VoiceAiTranslationRetryClient;
import jp.co.translacat.infrastructure.client.ai.server.voice.dto.AiVoiceTranslationRetryResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
            sessionCommandService.finishCompletion(
                    userId,
                    sessionId
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
            failCompletion(userId, sessionId);
        } catch (ExecutionException | TimeoutException e) {
            failCompletion(userId, sessionId);
        }
    }

    private void failCompletion(
            Long userId,
            String sessionId
    ) {
        sessionCommandService.fail(userId, sessionId);
        throw new BusinessException(
                "Timed out while flushing Voice streams.",
                VoiceErrorCode.COMPLETE_TIMEOUT
        );
    }
}
