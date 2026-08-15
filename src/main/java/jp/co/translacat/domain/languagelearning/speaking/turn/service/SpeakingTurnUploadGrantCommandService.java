package jp.co.translacat.domain.languagelearning.speaking.turn.service;

import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionPolicySnapshot;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionLifecycleService;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionPolicySnapshotService;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionQueryService;
import jp.co.translacat.domain.languagelearning.speaking.turn.dto.request.SpeakingTurnUploadGrantRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.turn.dto.response.SpeakingTurnUploadGrantResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.speaking.turn.repository.SpeakingTurnRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpeakingTurnUploadGrantCommandService {

    private static final int UPLOAD_GRANT_MINUTES = 10;

    private final SpeakingTurnRepository turnRepository;
    private final SpeakingSessionQueryService sessionQueryService;
    private final SpeakingSessionLifecycleService lifecycleService;
    private final SpeakingSessionPolicySnapshotService snapshotService;

    @Transactional
    public SpeakingTurnUploadGrantResponseDto create(
            Long userId,
            Long sessionId,
            SpeakingTurnUploadGrantRequestDto request
    ) {
        SpeakingSession session = sessionQueryService.getOwnedEntityForUpdate(
                userId,
                sessionId
        );
        lifecycleService.expireIfNeeded(session);
        lifecycleService.requireActive(session);
        validate(request, session);

        SpeakingSessionPolicySnapshot snapshot = snapshotService.read(session);
        if (session.getTotalDurationSeconds() >= snapshot.maxSessionSeconds()) {
            throw new BusinessException(
                    "Speaking Session 최대 시간이 종료되었습니다.",
                    LanguageLearningErrorCode.SESSION_NOT_ACTIVE
            );
        }

        return turnRepository.findBySessionIdAndIdempotencyKey(
                sessionId,
                request.idempotencyKey()
        ).map(this::toResponse)
                .orElseGet(() -> createNew(session, request));
    }

    private SpeakingTurnUploadGrantResponseDto createNew(
            SpeakingSession session,
            SpeakingTurnUploadGrantRequestDto request
    ) {
        if (turnRepository.findBySessionIdAndTurnIndex(
                session.getId(),
                request.turnIndex()
        ).isPresent()) {
            throw new BusinessException(
                    "이미 존재하는 Turn Index입니다.",
                    LanguageLearningErrorCode.TURN_ALREADY_EXISTS
            );
        }

        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusMinutes(UPLOAD_GRANT_MINUTES);
        SpeakingTurn turn = turnRepository.save(
                SpeakingTurn.createUploadGrant(
                        session,
                        request.turnIndex(),
                        request.idempotencyKey(),
                        token,
                        expiresAt
                )
        );
        return toResponse(turn);
    }

    private void validate(
            SpeakingTurnUploadGrantRequestDto request,
            SpeakingSession session
    ) {
        if (request == null
                || request.idempotencyKey() == null
                || request.idempotencyKey().isBlank()) {
            throw new BusinessException(
                    "Turn Idempotency Key가 필요합니다.",
                    LanguageLearningErrorCode.INVALID_TURN_ORDER
            );
        }
        int expected = session.getCompletedTurns() + 1;
        if (request.turnIndex() != expected
                || request.turnIndex() > session.getMaxTurns()) {
            throw new BusinessException(
                    "Turn 순서가 유효하지 않습니다.",
                    LanguageLearningErrorCode.INVALID_TURN_ORDER
            );
        }
    }

    private SpeakingTurnUploadGrantResponseDto toResponse(SpeakingTurn turn) {
        return new SpeakingTurnUploadGrantResponseDto(
                turn.getId(),
                turn.getTurnIndex(),
                turn.getUploadToken(),
                "/api/v1/language-learning/speaking/sessions/"
                        + turn.getSession().getId()
                        + "/turns",
                turn.getUploadExpiresAt()
        );
    }
}
