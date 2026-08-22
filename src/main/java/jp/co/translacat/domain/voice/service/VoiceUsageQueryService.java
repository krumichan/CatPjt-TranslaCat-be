package jp.co.translacat.domain.voice.service;

import jp.co.translacat.domain.voice.config.VoicePolicyProperties;
import jp.co.translacat.domain.voice.entity.VoiceSession;
import jp.co.translacat.domain.voice.repository.VoiceSessionRepository;
import jp.co.translacat.domain.voice.repository.VoiceUsageLedgerRepository;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoiceUsageQueryService {

    private final VoiceSessionRepository sessionRepository;
    private final VoiceUsageLedgerRepository usageRepository;
    private final VoicePolicyProperties policy;

    public boolean isLimitReached(
            Long userId,
            String sessionId
    ) {
        VoiceSession session = sessionRepository
                .findByIdAndUser_Id(sessionId, userId)
                .orElseThrow(() -> new BusinessException(
                        "Voice resource was not found.",
                        VoiceErrorCode.NOT_FOUND
                ));

        return session.getProcessedAudioMs() >= policy.getMaxSessionMs()
                || usageRepository.sumProcessedAudioMs(
                        userId,
                        LocalDate.now()
                ) >= policy.getDailyLimitMs();
    }
}
