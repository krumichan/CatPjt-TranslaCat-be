package jp.co.translacat.domain.voice.service;

import jp.co.translacat.domain.voice.config.VoicePolicyProperties;
import jp.co.translacat.domain.voice.enums.VoiceSessionStatus;
import jp.co.translacat.domain.voice.repository.VoiceSessionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoiceStaleSessionQueryService {

    private static final EnumSet<VoiceSessionStatus> STALE_STATUSES =
            EnumSet.of(
                    VoiceSessionStatus.CREATED,
                    VoiceSessionStatus.ACTIVE,
                    VoiceSessionStatus.DEGRADED,
                    VoiceSessionStatus.COMPLETING
            );
    private static final int CLEANUP_BATCH_SIZE = 50;

    private final VoiceSessionRepository sessionRepository;
    private final VoicePolicyProperties policy;

    public List<StaleSession> findStale() {
        return sessionRepository.findStale(
                        STALE_STATUSES,
                        LocalDateTime.now().minusMinutes(
                                policy.getStaleSessionMinutes()
                        ),
                        CLEANUP_BATCH_SIZE
                )
                .stream()
                .map(session -> new StaleSession(
                        session.getId(),
                        session.getUser().getId(),
                        session.isSaveTranscript()
                ))
                .toList();
    }

    public record StaleSession(
            String sessionId,
            Long userId,
            boolean saveTranscript
    ) {
    }
}
