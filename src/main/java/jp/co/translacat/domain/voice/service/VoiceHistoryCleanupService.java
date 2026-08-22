package jp.co.translacat.domain.voice.service;

import jp.co.translacat.domain.voice.service.VoiceStaleSessionQueryService.StaleSession;
import jp.co.translacat.domain.voice.websocket.service.VoiceConnectionRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceHistoryCleanupService {

    private static final int CLOSE_TIMEOUT_SECONDS = 2;

    private final VoiceStaleSessionQueryService staleSessionQueryService;
    private final VoiceSessionCommandService sessionCommandService;
    private final VoiceConnectionRegistry connectionRegistry;

    @Scheduled(
            fixedDelayString = "${translacat.voice.cleanup-fixed-delay-ms:60000}",
            initialDelayString = "${translacat.voice.cleanup-initial-delay-ms:60000}"
    )
    public void cleanupStaleSessions() {
        List<StaleSession> staleSessions =
                staleSessionQueryService.findStale();

        for (StaleSession session : staleSessions) {
            closeConnections(session.sessionId());
            sessionCommandService.cleanupStale(
                    session.userId(),
                    session.sessionId(),
                    session.saveTranscript()
            );

            log.info(
                    "Voice stale session cleaned sessionId={} saveTranscript={}",
                    session.sessionId(),
                    session.saveTranscript()
            );
        }
    }

    private void closeConnections(String sessionId) {
        try {
            connectionRegistry.closeSession(
                            sessionId,
                            "STALE_SESSION"
                    )
                    .get(
                            CLOSE_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    );
        } catch (Exception ignored) {
            // DB cleanup is the final safety net for stale sessions.
        }
    }
}
