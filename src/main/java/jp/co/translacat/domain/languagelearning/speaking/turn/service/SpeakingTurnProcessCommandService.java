package jp.co.translacat.domain.languagelearning.speaking.turn.service;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingTurnProcessResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.port.SpeakingAiClient;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.AssistanceType;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingStage;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingTurnStatus;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionPolicySnapshot;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionCompletionCommandService;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionLifecycleService;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionPolicySnapshotService;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionQueryService;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionUsageQueryService;
import jp.co.translacat.domain.languagelearning.speaking.turn.dto.request.SpeakingTurnProcessRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.speaking.turn.factory.SpeakingTurnAiRequestFactory;
import jp.co.translacat.domain.languagelearning.speaking.turn.policy.SpeakingTurnCompletionPolicy;
import jp.co.translacat.domain.languagelearning.speaking.turn.repository.SpeakingTurnRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SpeakingTurnProcessCommandService {

    private final SpeakingTurnRepository turnRepository;
    private final SpeakingTurnQueryService turnQueryService;
    private final SpeakingSessionQueryService sessionQueryService;
    private final SpeakingSessionLifecycleService sessionLifecycleService;
    private final SpeakingSessionPolicySnapshotService snapshotService;
    private final SpeakingSessionUsageQueryService usageQueryService;
    private final SpeakingSessionCompletionCommandService completionCommandService;
    private final SpeakingTurnAudioService audioService;
    private final SpeakingAiClient speakingAiClient;
    private final SpeakingTurnAiRequestFactory aiRequestFactory;
    private final SpeakingTurnAiResponseService responseService;
    private final SpeakingTurnCompletionPolicy completionPolicy;
    private final LanguageLearningJsonCodec jsonCodec;

    @Transactional(noRollbackFor = BusinessException.class)
    public SpeakingTurn process(
            Long userId,
            Long sessionId,
            SpeakingTurnProcessRequestDto request,
            MultipartFile audio
    ) {
        if (request == null || request.turnId() == null) {
            throw invalid("Turn ID가 필요합니다.");
        }

        SpeakingSession session = activeSession(userId, sessionId);
        SpeakingTurn turn = turnQueryService.getOwnedEntityForUpdate(
                userId,
                sessionId,
                request.turnId()
        );
        if (shouldReturnExisting(turn)) {
            return turn;
        }
        if (!turn.isUploadTokenValid(request.uploadToken(), LocalDateTime.now())) {
            throw new BusinessException(
                    "Audio Upload Token이 만료되었거나 유효하지 않습니다.",
                    LanguageLearningErrorCode.AUDIO_UPLOAD_EXPIRED
            );
        }

        SpeakingSessionPolicySnapshot snapshot = snapshotService.read(session);
        byte[] audioBytes = audioService.readAndValidate(
                audio,
                request.durationSeconds(),
                snapshot
        );
        if (turn.getUserAudioObjectKey() == null) {
            usageQueryService.requireTurnAllowed(
                    userId,
                    session.getLearningDate(),
                    Math.round(request.durationSeconds()),
                    snapshot
            );
        }
        List<AssistanceType> assistance = safe(request.assistanceUsage());
        audioService.storeUserAudio(
                userId,
                session,
                turn,
                audio,
                audioBytes,
                request.durationSeconds(),
                assistance,
                snapshot
        );

        turn.markProcessing();
        AiSpeakingTurnProcessResponseDto response = callTurnAi(
                session,
                turn,
                audioBytes,
                audio.getOriginalFilename(),
                audioService.contentType(audio),
                assistance
        );
        responseService.apply(session, turn, response, snapshot);
        completeIfNeeded(userId, session, response, snapshot);
        return turn;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public SpeakingTurn retry(
            Long userId,
            Long sessionId,
            Long turnId
    ) {
        SpeakingSession session = activeSession(userId, sessionId);
        SpeakingTurn turn = turnQueryService.getOwnedEntityForUpdate(
                userId,
                sessionId,
                turnId
        );
        if (turn.getStatus() == SpeakingTurnStatus.READY
                || turn.getStatus() == SpeakingTurnStatus.EXCLUDED) {
            return turn;
        }
        if (turn.getStatus() == SpeakingTurnStatus.PROCESSING) {
            return turn;
        }
        if (turn.getStatus() != SpeakingTurnStatus.PARTIAL_FAILURE
                && turn.getStatus() != SpeakingTurnStatus.FAILED) {
            throw new BusinessException(
                    "재시도 가능한 Turn 상태가 아닙니다.",
                    LanguageLearningErrorCode.TURN_PROCESSING
            );
        }

        SpeakingSessionPolicySnapshot snapshot = snapshotService.read(session);
        if (turn.getManualRetryCount()
                >= snapshot.manualRetryLimitPerStage()) {
            throw new BusinessException(
                    "수동 재시도 가능 횟수를 초과했습니다.",
                    LanguageLearningErrorCode.TURN_PROCESSING
            );
        }
        turn.incrementManualRetry();

        if (turn.getFailedStage() == SpeakingStage.TTS
                && turn.getAssistantText() != null) {
            responseService.retryTts(session, turn, snapshot);
            completeIfNeeded(userId, session, null, snapshot);
            return turn;
        }

        byte[] audioBytes = audioService.loadUserAudio(turn);
        turn.markProcessing();
        AiSpeakingTurnProcessResponseDto response = callTurnAi(
                session,
                turn,
                audioBytes,
                turn.getUserAudioFileName(),
                turn.getUserAudioContentType(),
                assistanceFrom(turn)
        );
        responseService.apply(session, turn, response, snapshot);
        completeIfNeeded(userId, session, response, snapshot);
        return turn;
    }

    private SpeakingSession activeSession(Long userId, Long sessionId) {
        SpeakingSession session = sessionQueryService.getOwnedEntity(
                userId,
                sessionId
        );
        sessionLifecycleService.expireIfNeeded(session);
        sessionLifecycleService.requireActive(session);
        return session;
    }

    private AiSpeakingTurnProcessResponseDto callTurnAi(
            SpeakingSession session,
            SpeakingTurn turn,
            byte[] audioBytes,
            String fileName,
            String contentType,
            List<AssistanceType> assistance
    ) {
        try {
            return speakingAiClient.processTurn(
                    aiRequestFactory.create(
                            session,
                            turn,
                            previousTurns(
                                    session.getId(),
                                    turn.getTurnIndex()
                            ),
                            assistance
                    ),
                    audioBytes,
                    fileName,
                    contentType
            );
        } catch (BusinessException e) {
            SpeakingStage stage = stageFromErrorCode(e.getErrorCode());
            turn.markFailed(
                    stage,
                    errorCode(stage, e.getErrorCode()),
                    truncate(e.getMessage(), 1000)
            );
            throw e;
        } catch (RuntimeException e) {
            turn.markFailed(
                    SpeakingStage.STT,
                    LanguageLearningErrorCode.STT_FAILED,
                    truncate(e.getMessage(), 1000)
            );
            throw new BusinessException(
                    "Speaking Turn 처리에 실패했습니다.",
                    LanguageLearningErrorCode.STT_FAILED
            );
        }
    }

    private boolean shouldReturnExisting(SpeakingTurn turn) {
        return turn.getStatus() != SpeakingTurnStatus.AWAITING_UPLOAD;
    }

    private void completeIfNeeded(
            Long userId,
            SpeakingSession session,
            AiSpeakingTurnProcessResponseDto response,
            SpeakingSessionPolicySnapshot snapshot
    ) {
        if (completionPolicy.shouldComplete(session, response, snapshot)) {
            completionCommandService.complete(userId, session.getId());
        }
    }

    private List<SpeakingTurn> previousTurns(Long sessionId, int turnIndex) {
        return turnRepository.findAllBySessionIdOrderByTurnIndexAsc(sessionId)
                .stream()
                .filter(turn -> turn.getTurnIndex() < turnIndex)
                .toList();
    }

    private List<AssistanceType> assistanceFrom(SpeakingTurn turn) {
        if (turn.getAssistanceUsageJson() == null
                || turn.getAssistanceUsageJson().isBlank()) {
            return List.of();
        }
        return jsonCodec.read(
                turn.getAssistanceUsageJson(),
                new TypeReference<List<AssistanceType>>() {
                }
        );
    }

    private SpeakingStage stageFromErrorCode(String errorCode) {
        if (LanguageLearningErrorCode.TTS_FAILED.equals(errorCode)) {
            return SpeakingStage.TTS;
        }
        if (LanguageLearningErrorCode.STT_FAILED.equals(errorCode)) {
            return SpeakingStage.STT;
        }
        return SpeakingStage.CONVERSATION;
    }

    private String errorCode(SpeakingStage stage, String actual) {
        if (actual != null && !actual.isBlank()) {
            return actual;
        }
        return switch (stage) {
            case TTS -> LanguageLearningErrorCode.TTS_FAILED;
            case STT, AUDIO_VALIDATION -> LanguageLearningErrorCode.STT_FAILED;
            default -> LanguageLearningErrorCode.SPEAKING_EVALUATION_FAILED;
        };
    }

    private List<AssistanceType> safe(List<AssistanceType> values) {
        return values == null ? List.of() : values;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.INVALID_AUDIO
        );
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
