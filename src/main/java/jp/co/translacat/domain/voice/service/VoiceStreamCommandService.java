package jp.co.translacat.domain.voice.service;

import jp.co.translacat.domain.voice.config.VoicePolicyProperties;
import jp.co.translacat.domain.voice.entity.VoiceSession;
import jp.co.translacat.domain.voice.entity.VoiceSessionChannel;
import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.enums.VoiceChannelStatus;
import jp.co.translacat.domain.voice.model.VoiceStreamContext;
import jp.co.translacat.domain.voice.repository.VoiceSegmentRepository;
import jp.co.translacat.domain.voice.repository.VoiceSessionChannelRepository;
import jp.co.translacat.domain.voice.repository.VoiceSessionRepository;
import jp.co.translacat.domain.voice.repository.VoiceUsageLedgerRepository;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;

@Service
@RequiredArgsConstructor
public class VoiceStreamCommandService {

    private static final EnumSet<VoiceChannelStatus> OPERATIONAL_STATUSES =
            EnumSet.of(
                    VoiceChannelStatus.STREAMING,
                    VoiceChannelStatus.BACKPRESSURED
            );

    private final VoiceSessionRepository sessionRepository;
    private final VoiceSessionChannelRepository channelRepository;
    private final VoiceSegmentRepository segmentRepository;
    private final VoiceUsageLedgerRepository usageRepository;
    private final VoicePolicyProperties policy;

    @Transactional
    public VoiceStreamContext open(
            Long userId,
            String sessionId,
            VoiceChannel channel,
            String connectionId
    ) {
        VoiceSession session = sessionRepository
                .findOwnedForUpdate(sessionId, userId)
                .orElseThrow(this::notFound);

        validateStreamable(userId, session);

        VoiceSessionChannel sessionChannel = channelRepository
                .findOwnedForUpdate(
                        sessionId,
                        channel,
                        userId
                )
                .orElseThrow(this::notFound);
        sessionChannel.connecting(connectionId);

        return new VoiceStreamContext(
                userId,
                sessionId,
                channel,
                connectionId,
                session.getMode(),
                session.getSourceLanguageMode(),
                sessionChannel.getManualSourceLanguage(),
                sessionChannel.getLastLockedLanguage(),
                session.getTargetLanguage(),
                session.getPolicySnapshot(),
                segmentRepository.findMaxSequence(sessionId, channel)
        );
    }

    @Transactional
    public void markStreaming(VoiceStreamContext context) {
        VoiceSession session = requireSession(context);
        VoiceSessionChannel channel = requireChannel(context);
        channel.streaming(context.connectionId());
        recomputeSession(session);
    }

    @Transactional
    public void markBackpressured(VoiceStreamContext context) {
        VoiceSession session = requireSession(context);
        VoiceSessionChannel channel = requireChannel(context);
        channel.backpressured(context.connectionId());
        recomputeSession(session);
    }

    @Transactional
    public void markResumed(VoiceStreamContext context) {
        VoiceSession session = requireSession(context);
        VoiceSessionChannel channel = requireChannel(context);
        channel.resumed(context.connectionId());
        recomputeSession(session);
    }

    @Transactional
    public void markError(VoiceStreamContext context) {
        VoiceSession session = requireSession(context);
        VoiceSessionChannel channel = requireChannel(context);
        channel.error(context.connectionId());
        recomputeSession(session);
    }

    @Transactional
    public void markDisconnected(VoiceStreamContext context) {
        VoiceSession session = sessionRepository
                .findOwnedForUpdate(
                        context.sessionId(),
                        context.userId()
                )
                .orElse(null);
        if (session == null) {
            return;
        }

        VoiceSessionChannel channel = channelRepository
                .findOwnedForUpdate(
                        context.sessionId(),
                        context.channel(),
                        context.userId()
                )
                .orElse(null);
        if (channel == null) {
            return;
        }

        channel.disconnected(context.connectionId());
        recomputeSession(session);
    }

    @Transactional
    public void updateLockedLanguage(
            VoiceStreamContext context,
            String language
    ) {
        if (language == null || language.isBlank()) {
            return;
        }

        requireSession(context);
        VoiceSessionChannel channel = requireChannel(context);
        channel.lockLanguage(language);
    }

    private void validateStreamable(
            Long userId,
            VoiceSession session
    ) {
        if (!policy.isEnabled()) {
            throw new BusinessException(
                    "Voice Translation is disabled.",
                    VoiceErrorCode.FEATURE_DISABLED
            );
        }
        if (!session.getStatus().isOpen()) {
            throw new BusinessException(
                    "Voice session is not open for streaming.",
                    VoiceErrorCode.SESSION_NOT_STREAMABLE
            );
        }
        if (session.getProcessedAudioMs() >= policy.getMaxSessionMs()) {
            throw new BusinessException(
                    "Voice session duration limit has been reached.",
                    VoiceErrorCode.SESSION_LIMIT_EXCEEDED
            );
        }

        long usedToday = usageRepository.sumProcessedAudioMs(
                userId,
                LocalDate.now()
        );
        if (usedToday >= policy.getDailyLimitMs()) {
            throw new BusinessException(
                    "Daily Voice usage limit has been reached.",
                    VoiceErrorCode.DAILY_LIMIT_EXCEEDED
            );
        }
    }

    private VoiceSession requireSession(VoiceStreamContext context) {
        return sessionRepository.findOwnedForUpdate(
                        context.sessionId(),
                        context.userId()
                )
                .orElseThrow(this::notFound);
    }

    private VoiceSessionChannel requireChannel(
            VoiceStreamContext context
    ) {
        return channelRepository.findOwnedForUpdate(
                        context.sessionId(),
                        context.channel(),
                        context.userId()
                )
                .orElseThrow(this::notFound);
    }

    private void recomputeSession(VoiceSession session) {
        long operationalChannels = channelRepository
                .countBySession_IdAndStatusIn(
                        session.getId(),
                        OPERATIONAL_STATUSES
                );

        session.updateConnectivity(
                Math.toIntExact(operationalChannels)
        );
    }

    private BusinessException notFound() {
        return new BusinessException(
                "Voice stream resource was not found.",
                VoiceErrorCode.NOT_FOUND
        );
    }
}
