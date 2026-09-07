package jp.co.translacat.domain.languagelearning.level.service;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelStatusResponseDto;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;
import jp.co.translacat.domain.languagelearning.level.policy.LevelTestScoringPolicy;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestSessionRepository;
import jp.co.translacat.domain.languagelearning.profile.entity.LearningProfile;
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LevelTestQueryService {

    private final LevelTestSessionRepository sessionRepository;
    private final LearningProfileQueryService profileQueryService;
    private final LevelTestScoringPolicy scoringPolicy;

    @Transactional
    public LevelStatusResponseDto getStatus(Long userId) {
        LearningProfile profile = profileQueryService.getOrCreate(userId);
        Optional<LevelTestSession> activeSession = getActiveSession(userId);
        Double score = profile.getBaseLevelScore();

        return new LevelStatusResponseDto(
                profile.getState(),
                hasCompletedInitialTest(userId),
                isRecheckRecommended(userId),
                activeSession.map(LevelTestSession::getId).orElse(null),
                activeSession.map(LevelTestSession::currentQuestionNumber)
                        .orElse(null),
                score,
                score == null ? null : scoringPolicy.band(
                        (int) Math.round(score)
                )
        );
    }

    @Transactional(readOnly = true)
    public Optional<LevelTestSession> getActiveSession(Long userId) {
        return sessionRepository
                .findTopByUserIdAndStatusOrderByStartedAtDesc(
                        userId,
                        LevelTestSessionStatus.IN_PROGRESS
                )
                .or(() -> sessionRepository
                        .findTopByUserIdAndStatusOrderByStartedAtDesc(
                                userId,
                                LevelTestSessionStatus.EVALUATING
                        ));
    }

    @Transactional(readOnly = true)
    public LevelTestSession getOwnedSession(
            Long userId,
            Long sessionId
    ) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(
                        "Level Test Session을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.LEVEL_TEST_NOT_FOUND
                ));
    }

    @Transactional(readOnly = true)
    public boolean hasCompletedInitialTest(Long userId) {
        return sessionRepository
                .findTopByUserIdAndSessionTypeAndStatusOrderByCompletedAtDesc(
                        userId,
                        LevelTestSessionType.INITIAL,
                        LevelTestSessionStatus.COMPLETED
                )
                .isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isRecheckRecommended(Long userId) {
        LocalDateTime latest = sessionRepository
                .findAllByUserIdAndStatusOrderByCompletedAtDesc(
                        userId,
                        LevelTestSessionStatus.COMPLETED
                )
                .stream()
                .map(LevelTestSession::getCompletedAt)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);

        return latest != null
                && Duration.between(latest, LocalDateTime.now()).toDays() >= 30;
    }
}
