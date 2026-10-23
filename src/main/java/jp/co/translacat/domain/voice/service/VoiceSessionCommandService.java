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
import jp.co.translacat.domain.voice.enums.VoiceChannelStatus;
import jp.co.translacat.domain.voice.enums.VoiceMode;
import jp.co.translacat.domain.voice.enums.VoiceSegmentStatus;
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
import java.util.*;

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
        ValidatedCreateRequest validated = validateCreateRequest(request);

        User user = entityManager.find(
                User.class,
                userId,
                LockModeType.PESSIMISTIC_WRITE
        );
        if (user == null || !user.isActiveAccount()) {
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
        VoiceSession session = findFreshSessionForCompletion(sessionId, userId)
                .orElseThrow(this::notFound);

        // 이미 확정된 종료를 다시 요청해도 실패 세션을 재활성화하거나 INVALID_STATE로 덮지 않는다.
        if (session.getStatus() == VoiceSessionStatus.COMPLETED
                || session.getStatus() == VoiceSessionStatus.FAILED) {
            return false;
        }

        session.startCompleting();
        return true;
    }

    @Transactional
    public boolean finishCompletion(
            Long userId,
            String sessionId
    ) {
        VoiceSession session = findFreshSessionForCompletion(sessionId, userId)
                .orElseThrow(this::notFound);

        // 다른 종료 요청이 먼저 실패를 확정했어도 같은 정리를 반복하고 실패 상태를 보존한다.
        if (session.getStatus() == VoiceSessionStatus.FAILED) {
            cleanupFailedTranscripts(session, sessionId);
            return false;
        }

        if (session.getStatus() != VoiceSessionStatus.COMPLETED) {
            // 브라우저가 먼저 소켓을 닫아 registry에서 사라진 실패도 DB 채널 상태로 확인한다.
            // 재연결에 성공한 채널은 ERROR가 해제되어 정상 완료할 수 있다.
            if (channelRepository.countBySession_IdAndStatusIn(
                    sessionId,
                    EnumSet.of(VoiceChannelStatus.ERROR)
            ) > 0) {
                session.fail();
                cleanupFailedTranscripts(session, sessionId);
                return false;
            }

            session.complete();
        }

        // 재연결 전 유실된 번역과 이전 버전이 남긴 미완료 행도 정상 API 재호출로 정리한다.
        cleanupTranscripts(session, sessionId, VoiceErrorCode.AI_CONNECTION_CLOSED);
        return true;
    }

    @Transactional
    public void fail(
            Long userId,
            String sessionId
    ) {
        // Final 저장과 같은 세션 잠금 안에서 상태 확정과 미보관 데이터 삭제를 함께 처리한다.
        VoiceSession session = findFreshSessionForCompletion(sessionId, userId)
                .orElse(null);
        if (session == null) {
            return;
        }

        session.fail();
        cleanupFailedTranscripts(session, sessionId);
    }

    @Transactional
    public void cleanupStale(
            Long userId,
            String sessionId
    ) {
        VoiceSession session = sessionRepository
                .findOwnedForUpdate(sessionId, userId)
                .orElse(null);
        if (session == null || !session.getStatus().isActiveLike()) {
            return;
        }

        session.fail();
        cleanupFailedTranscripts(session, sessionId);
    }

    private void cleanupFailedTranscripts(VoiceSession session, String sessionId) {
        cleanupTranscripts(session, sessionId, VoiceErrorCode.COMPLETE_TIMEOUT);
    }

    private void cleanupTranscripts(VoiceSession session, String sessionId, String errorCode) {
        if (!session.isSaveTranscript()) {
            segmentRepository.deleteBySession_Id(sessionId);
            return;
        }

        // 보관한 원문은 유지하되 끝나지 않은 번역을 영구 진행 중 상태로 남기지 않는다.
        segmentRepository.findBySession_IdAndStatus(sessionId, VoiceSegmentStatus.TRANSLATING)
                .forEach(segment -> segment.markTranslationFailed(errorCode));
    }

    private Optional<VoiceSession> findFreshSessionForCompletion(String sessionId, Long userId) {
        // 완료/실패 트랜잭션 진입점에서만 사용한다. OSIV가 이전 트랜잭션의 엔티티를 보유해도
        // refresh와 row lock을 함께 적용해 flush 대기 중 worker가 commit한 사용량/version을 읽는다.
        var owned = sessionRepository.findByIdAndUser_Id(sessionId, userId);
        owned.ifPresent(session -> entityManager.refresh(session, LockModeType.PESSIMISTIC_WRITE));
        return owned;
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
