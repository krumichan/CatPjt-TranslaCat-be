package jp.co.translacat.domain.voice.service;

import jp.co.translacat.domain.voice.service.VoiceStaleSessionQueryService.StaleSession;
import jp.co.translacat.domain.voice.websocket.service.VoiceConnectionRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceHistoryCleanupService {

    private final VoiceStaleSessionQueryService staleSessionQueryService;
    private final VoiceSessionCommandService sessionCommandService;
    private final VoiceConnectionRegistry connectionRegistry;

    public void cleanupStaleSessions() {
        List<StaleSession> staleSessions =
                staleSessionQueryService.findStale();

        for (StaleSession session : staleSessions) {
            // Stale 상태와 미보관 삭제를 확정한 다음 소켓을 강제 종료한다.
            // 오래 멈춘 채널에는 다시 graceful drain을 기다릴 필요가 없다.
            try {
                sessionCommandService.cleanupStale(
                        session.userId(),
                        session.sessionId()
                );
            } finally {
                connectionRegistry.forceCloseSession(session.sessionId());
            }

            log.info(
                    "Voice stale session cleaned sessionId={} saveTranscript={}",
                    session.sessionId(),
                    session.saveTranscript()
            );
        }
    }
}
