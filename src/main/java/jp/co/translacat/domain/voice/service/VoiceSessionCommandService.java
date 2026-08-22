package jp.co.translacat.domain.voice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.voice.config.VoicePolicyProperties;
import jp.co.translacat.domain.voice.dto.request.VoiceSessionCreateRequestDto;
import jp.co.translacat.domain.voice.dto.request.VoiceSessionUpdateRequestDto;
import jp.co.translacat.domain.voice.entity.VoiceSession;
import jp.co.translacat.domain.voice.entity.VoiceSessionChannel;
import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.enums.VoiceMode;
import jp.co.translacat.domain.voice.enums.VoiceSessionStatus;
import jp.co.translacat.domain.voice.enums.VoiceSourceLanguageMode;
import jp.co.translacat.domain.voice.repository.VoiceSegmentRepository;
import jp.co.translacat.domain.voice.repository.VoiceSessionChannelRepository;
import jp.co.translacat.domain.voice.repository.VoiceSessionRepository;
import jp.co.translacat.domain.voice.repository.VoiceUsageLedgerRepository;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.domain.voice.support.VoicePolicy;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VoiceSessionCommandService {

    private static final EnumSet<VoiceSessionStatus> ACTIVE_STATUSES =
            EnumSet.of(
                    VoiceSessionStatus.CREATED,
                    VoiceSessionStatus.ACTIVE,
                    VoiceSessionStatus.DEGRADED,
                    VoiceSessionStatus.COMPLETING
            );

    private final EntityManager entityManager;
    private final VoiceSessionRepository sessionRepository;
    private final VoiceSessionChannelRepository channelRepository;
    private final VoiceSegmentRepository segmentRepository;
    private final VoiceUsageLedgerRepository usageRepository;
    private final VoicePolicyProperties policy;
    private final ObjectMapper objectMapper;

    @Transactional
    public String create(
            Long userId,
            VoiceSessionCreateRequestDto request
    ) {
        requireEnabled();
        ValidatedCreateRequest validated = validateCreateRequest(request);

        User user = entityManager.find(
                User.class,
                userId,
                LockModeType.PESSIMISTIC_WRITE
        );
        if (user == null) {
            throw notFound();
        }
        requireVoiceRole(user);

        if (!sessionRepository.findActiveByUserId(
                userId,
                ACTIVE_STATUSES
        ).isEmpty()) {
            throw new BusinessException(
                    "Only one active Voice session is allowed per user.",
                    VoiceErrorCode.ACTIVE_SESSION_EXISTS
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

        VoiceSession session = VoiceSession.create(
                user,
                validated.mode(),
                validated.sourceLanguageMode(),
                validated.targetLanguage(),
                validated.saveTranscript(),
                createPolicySnapshot()
        );
        sessionRepository.save(session);

        List<VoiceSessionChannel> channels = validated.mode()
                .allowedChannels()
                .stream()
                .sorted()
                .map(channel -> VoiceSessionChannel.create(
                        session,
                        channel,
                        validated.manualSourceLanguages().get(channel)
                ))
                .toList();
        channelRepository.saveAll(channels);

        return session.getId();
    }

    @Transactional
    public void update(
            Long userId,
            String sessionId,
            VoiceSessionUpdateRequestDto request
    ) {
        VoiceSession session = sessionRepository
                .findOwnedForUpdate(sessionId, userId)
                .orElseThrow(this::notFound);

        session.updateTitle(
                request == null
                        ? null
                        : request.title()
        );
    }

    @Transactional
    public boolean startCompleting(
            Long userId,
            String sessionId
    ) {
        VoiceSession session = sessionRepository
                .findOwnedForUpdate(sessionId, userId)
                .orElseThrow(this::notFound);

        if (session.getStatus() == VoiceSessionStatus.COMPLETED) {
            return false;
        }

        session.startCompleting();
        return true;
    }

    @Transactional
    public void finishCompletion(
            Long userId,
            String sessionId
    ) {
        VoiceSession session = sessionRepository
                .findOwnedForUpdate(sessionId, userId)
                .orElseThrow(this::notFound);

        if (session.getStatus() != VoiceSessionStatus.COMPLETED) {
            session.complete();
        }

        if (!session.isSaveTranscript()) {
            segmentRepository.deleteBySession_Id(sessionId);
        }
    }

    @Transactional
    public void fail(
            Long userId,
            String sessionId
    ) {
        sessionRepository.findOwnedForUpdate(sessionId, userId)
                .ifPresent(VoiceSession::fail);
    }

    @Transactional
    public void cleanupStale(
            Long userId,
            String sessionId,
            boolean saveTranscript
    ) {
        VoiceSession session = sessionRepository
                .findOwnedForUpdate(sessionId, userId)
                .orElse(null);
        if (session == null || !session.getStatus().isActiveLike()) {
            return;
        }

        session.fail();
        if (!saveTranscript) {
            segmentRepository.deleteBySession_Id(sessionId);
        }
    }

    @Transactional
    public void delete(
            Long userId,
            String sessionId
    ) {
        VoiceSession session = sessionRepository
                .findOwnedForUpdate(sessionId, userId)
                .orElseThrow(this::notFound);

        if (session.getStatus().isActiveLike()) {
            throw new BusinessException(
                    "Active Voice session cannot be deleted.",
                    VoiceErrorCode.ACTIVE_SESSION_DELETE_DENIED
            );
        }

        segmentRepository.deleteBySession_Id(sessionId);
        channelRepository.deleteBySession_Id(sessionId);
        sessionRepository.delete(session);
    }

    private ValidatedCreateRequest validateCreateRequest(
            VoiceSessionCreateRequestDto request
    ) {
        if (request == null || request.mode() == null) {
            throw new BusinessException(
                    "Voice mode is required.",
                    VoiceErrorCode.REQUEST_INVALID
            );
        }

        VoiceSourceLanguageMode sourceLanguageMode =
                request.sourceLanguageMode() == null
                        ? VoiceSourceLanguageMode.AUTO
                        : request.sourceLanguageMode();
        String targetLanguage = normalizeLanguage(request.targetLanguage());
        boolean saveTranscript = request.saveTranscript() == null
                || request.saveTranscript();

        Map<VoiceChannel, String> manualSourceLanguages =
                new EnumMap<>(VoiceChannel.class);
        if (request.manualSourceLanguages() != null) {
            manualSourceLanguages.putAll(request.manualSourceLanguages());
        }

        if (sourceLanguageMode == VoiceSourceLanguageMode.AUTO
                && !manualSourceLanguages.isEmpty()) {
            throw new BusinessException(
                    "manualSourceLanguages is only allowed in MANUAL mode.",
                    VoiceErrorCode.SOURCE_LANGUAGE_INVALID
            );
        }

        if (sourceLanguageMode == VoiceSourceLanguageMode.MANUAL) {
            EnumMap<VoiceChannel, String> normalized =
                    new EnumMap<>(VoiceChannel.class);
            for (VoiceChannel channel : request.mode().allowedChannels()) {
                normalized.put(
                        channel,
                        normalizeLanguage(manualSourceLanguages.get(channel))
                );
            }
            manualSourceLanguages = normalized;
        }

        return new ValidatedCreateRequest(
                request.mode(),
                sourceLanguageMode,
                targetLanguage,
                saveTranscript,
                manualSourceLanguages
        );
    }

    private String normalizeLanguage(String language) {
        if (language == null) {
            throw unsupportedLanguage();
        }

        String normalized = language.toLowerCase(Locale.ROOT);
        if (!VoicePolicy.SUPPORTED_LANGUAGES.contains(normalized)) {
            throw unsupportedLanguage();
        }
        return normalized;
    }

    private String createPolicySnapshot() {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "maxSessionMs", policy.getMaxSessionMs(),
                    "dailyLimitMs", policy.getDailyLimitMs(),
                    "endpointingSilenceMs", policy.getEndpointingSilenceMs(),
                    "minUtteranceDurationMs", policy.getMinUtteranceDurationMs(),
                    "maxUtteranceDurationMs", policy.getMaxUtteranceDurationMs(),
                    "languageLockConfidence", policy.getLanguageLockConfidence(),
                    "languageSwitchConfidence", policy.getLanguageSwitchConfidence(),
                    "languageSwitchConsecutiveCount", policy.getLanguageSwitchConsecutiveCount()
            ));
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    "Failed to build Voice policy snapshot.",
                    e
            );
        }
    }

    private void requireEnabled() {
        if (!policy.isEnabled()) {
            throw new BusinessException(
                    "Voice Translation is disabled.",
                    VoiceErrorCode.FEATURE_DISABLED
            );
        }
    }

    private void requireVoiceRole(User user) {
        if (user.getAuthority() != Role.USER
                && user.getAuthority() != Role.ADMIN) {
            throw new BusinessException(
                    "Voice Translation is not allowed.",
                    VoiceErrorCode.ROLE_NOT_ALLOWED
            );
        }
    }

    private BusinessException unsupportedLanguage() {
        return new BusinessException(
                "Voice language must be one of ko, ja, en.",
                VoiceErrorCode.LANGUAGE_UNSUPPORTED
        );
    }

    private BusinessException notFound() {
        return new BusinessException(
                "Voice resource was not found.",
                VoiceErrorCode.NOT_FOUND
        );
    }

    private record ValidatedCreateRequest(
            VoiceMode mode,
            VoiceSourceLanguageMode sourceLanguageMode,
            String targetLanguage,
            boolean saveTranscript,
            Map<VoiceChannel, String> manualSourceLanguages
    ) {
    }
}
