package jp.co.translacat.domain.languagelearning.speaking.turn.service;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingPracticeMode;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionPolicySnapshot;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionLifecycleService;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionPolicySnapshotService;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionQueryService;
import jp.co.translacat.domain.languagelearning.speaking.session.policy.SpeakingSessionPolicy;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.repository.SpeakingReadAloudProblemEvaluationRepository;
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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpeakingTurnUploadGrantCommandService {

    private static final int UPLOAD_GRANT_MINUTES = 10;

    private final SpeakingTurnRepository turnRepository;
    private final SpeakingSessionQueryService sessionQueryService;
    private final SpeakingSessionLifecycleService lifecycleService;
    private final SpeakingSessionPolicySnapshotService snapshotService;
    private final SpeakingReadAloudProblemEvaluationRepository readAloudEvaluationRepository;

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
                        request.problemIndex(),
                        request.attemptIndex(),
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
        int expected = session.getPracticeMode() == SpeakingPracticeMode.READ_ALOUD
                ? turnRepository.findFirstBySessionIdOrderByTurnIndexDesc(
                        session.getId()
                ).map(turn -> turn.getTurnIndex() + 1).orElse(1)
                : session.getCompletedTurns() + 1;
        if (request.turnIndex() != expected
                || request.turnIndex() > session.getMaxTurns()) {
            throw new BusinessException(
                    "Turn 순서가 유효하지 않습니다.",
                    LanguageLearningErrorCode.INVALID_TURN_ORDER
            );
        }

        if (session.getPracticeMode() == SpeakingPracticeMode.READ_ALOUD) {
            validateReadAloudSlot(request, session);
        } else if (request.problemIndex() != null || request.attemptIndex() != null) {
            throw new BusinessException(
                    "대화형 Speaking에는 문제/발화 Slot을 지정할 수 없습니다.",
                    LanguageLearningErrorCode.INVALID_TURN_ORDER
            );
        }
    }

    @Transactional
    public SpeakingTurnUploadGrantResponseDto createRerecord(
            Long userId,
            Long sessionId,
            Long turnId
    ) {
        SpeakingSession session = sessionQueryService.getOwnedEntityForUpdate(
                userId,
                sessionId
        );
        lifecycleService.expireIfNeeded(session);
        lifecycleService.requireActive(session);
        if (session.getPracticeMode() != SpeakingPracticeMode.READ_ALOUD) {
            throw new BusinessException(
                    "재녹음 Slot 교체는 듣고 리피트에서만 사용할 수 있습니다.",
                    LanguageLearningErrorCode.INVALID_TURN_ORDER
            );
        }
        SpeakingTurn turn = turnRepository
                .findOneByIdAndSessionIdAndSessionUserId(
                        turnId,
                        sessionId,
                        userId
                )
                .orElseThrow(() -> new BusinessException(
                        "Speaking Turn을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.TURN_NOT_FOUND
                ));
        if (turn.getProblemIndex() != null
                && readAloudEvaluationRepository.findBySessionIdAndProblemIndex(
                        sessionId,
                        turn.getProblemIndex()
                ).isPresent()) {
            throw new BusinessException(
                    "평가를 요청한 문제의 발화는 재녹음할 수 없습니다.",
                    LanguageLearningErrorCode.TURN_PROCESSING
            );
        }
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusMinutes(UPLOAD_GRANT_MINUTES);
        turn.renewUploadGrant(token, expiresAt);
        return toResponse(turn);
    }

    private void validateReadAloudSlot(
            SpeakingTurnUploadGrantRequestDto request,
            SpeakingSession session
    ) {
        Integer problemIndex = request.problemIndex();
        Integer attemptIndex = request.attemptIndex();
        if (problemIndex == null
                || problemIndex < 1
                || problemIndex > SpeakingSessionPolicy.READ_ALOUD_DAILY_ITEM_COUNT
                || attemptIndex == null
                || attemptIndex < 1
                || attemptIndex > SpeakingSessionPolicy.READ_ALOUD_MAX_ATTEMPTS_PER_ITEM) {
            throw new BusinessException(
                    "듣고 리피트 문제/발화 Slot이 유효하지 않습니다.",
                    LanguageLearningErrorCode.INVALID_TURN_ORDER
            );
        }
        List<Integer> submittedProblems = readAloudEvaluationRepository
                .findAllBySessionIdOrderByProblemIndexAsc(session.getId())
                .stream()
                .map(item -> item.getProblemIndex())
                .distinct()
                .toList();
        int expectedProblemIndex = Math.min(
                submittedProblems.size() + 1,
                SpeakingSessionPolicy.READ_ALOUD_DAILY_ITEM_COUNT
        );
        if (problemIndex != expectedProblemIndex
                || submittedProblems.contains(problemIndex)) {
            throw new BusinessException(
                    "현재 진행 중인 듣고 리피트 문제의 발화만 등록할 수 있습니다.",
                    LanguageLearningErrorCode.INVALID_TURN_ORDER
            );
        }

        if (turnRepository.findBySessionIdAndProblemIndexAndAttemptIndex(
                session.getId(),
                problemIndex,
                attemptIndex
        ).isPresent()) {
            throw new BusinessException(
                    "이미 존재하는 듣고 리피트 발화 Slot입니다.",
                    LanguageLearningErrorCode.TURN_ALREADY_EXISTS
            );
        }
        long existingAttempts = turnRepository
                .findAllBySessionIdAndProblemIndexOrderByAttemptIndexAsc(
                        session.getId(),
                        problemIndex
                ).size();
        if (attemptIndex != existingAttempts + 1) {
            throw new BusinessException(
                    "듣고 리피트 발화 순서가 유효하지 않습니다.",
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
