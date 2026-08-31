package jp.co.translacat.domain.languagelearning.level.pool.service;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionStatus;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;
import jp.co.translacat.domain.languagelearning.level.policy.LevelTestRecipe;
import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionPool;
import jp.co.translacat.domain.languagelearning.level.pool.policy.LevelTestQuestionPoolPolicy;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestItemRepository;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestSessionRepository;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestQuestionService;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityContext;
import jp.co.translacat.domain.languagelearning.quality.service.GenerationDiversityContextService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LevelTestQuestionPrefetchService {

    private final LevelTestSessionRepository sessionRepository;
    private final LevelTestItemRepository itemRepository;
    private final LevelTestRecipe recipe;
    private final LevelTestQuestionService questionService;
    private final LevelTestQuestionPoolQueryService poolQueryService;
    private final LevelTestQuestionCandidateCommandService candidateCommandService;
    private final LevelTestQuestionPoolPolicy poolPolicy;
    private final GenerationDiversityContextService diversityContextService;

    public void prefetch(
            Long sessionId,
            int questionNumber,
            int complexityBand
    ) {
        long startedAt = System.nanoTime();
        if (questionNumber < 1
                || questionNumber > LevelTestRecipe.TOTAL_QUESTIONS
                || itemRepository.findBySessionIdAndQuestionNumber(
                        sessionId,
                        questionNumber
                ).isPresent()) {
            return;
        }

        LevelTestSession session = sessionRepository.findById(sessionId)
                .orElse(null);
        if (!canPrefetch(session, questionNumber)) {
            return;
        }

        LevelTestQuestionCandidateCommandService.Reservation reservation =
                candidateCommandService.reserve(
                        sessionId,
                        questionNumber,
                        complexityBand
                );
        if (!reservation.acquired()) {
            return;
        }

        try {
            LevelTestRecipe.Entry expected = recipe.entry(questionNumber);
            LevelTestQuestionPool poolQuestion = reusablePoolQuestion(
                    session,
                    expected,
                    questionNumber,
                    complexityBand
            );
            String source = "QUESTION_POOL";
            if (poolQuestion == null) {
                poolQuestion = questionService.generatePoolCandidate(
                        session,
                        questionNumber,
                        complexityBand
                );
                source = "AI_GENERATED";
            }
            if (poolQuestion == null) {
                candidateCommandService.markFailed(
                        reservation.candidateId()
                );
                log.info(
                        "Level Test prefetch skipped duplicate. sessionId={}, questionNumber={}, band={}, elapsedMs={}",
                        sessionId,
                        questionNumber,
                        complexityBand,
                        elapsedMs(startedAt)
                );
                return;
            }

            if (itemRepository.findBySessionIdAndQuestionNumber(
                    sessionId,
                    questionNumber
            ).isPresent()) {
                candidateCommandService.expire(reservation.candidateId());
                return;
            }
            LevelTestSession latest = sessionRepository.findById(sessionId)
                    .orElse(null);
            if (!canPrefetch(latest, questionNumber)) {
                candidateCommandService.expire(reservation.candidateId());
                return;
            }

            candidateCommandService.markAvailable(
                    reservation.candidateId(),
                    poolQuestion.getId()
            );
            log.info(
                    "Level Test question prefetched. sessionId={}, questionNumber={}, band={}, source={}, poolQuestionId={}, elapsedMs={}",
                    sessionId,
                    questionNumber,
                    complexityBand,
                    source,
                    poolQuestion.getId(),
                    elapsedMs(startedAt)
            );
        } catch (RuntimeException exception) {
            candidateCommandService.markFailed(reservation.candidateId());
            throw exception;
        }
    }

    private LevelTestQuestionPool reusablePoolQuestion(
            LevelTestSession session,
            LevelTestRecipe.Entry expected,
            int questionNumber,
            int complexityBand
    ) {
        LevelTestQuestionPoolQueryService.PoolCounts counts =
                poolQueryService.counts(
                        session.getOriginLanguage(),
                        session.getLearningLanguage(),
                        expected.domain(),
                        expected.itemType(),
                        complexityBand,
                        LevelTestSession.DEFAULT_GENERATION_POLICY_VERSION,
                        LevelTestQuestionService.MODEL_CONFIG_VERSION
                );
        if (!poolPolicy.canReuse(
                counts.total(),
                counts.bucket(),
                expected.domain(),
                expected.itemType(),
                complexityBand
        )) {
            return null;
        }

        DiversityContext diversityContext = diversityContextService
                .levelTestContext(
                        session.getUser().getId(),
                        session.getLearningLanguage(),
                        session.getId()
                );
        return poolQueryService.findReusable(
                session.getOriginLanguage(),
                session.getLearningLanguage(),
                expected.domain(),
                expected.itemType(),
                complexityBand,
                LevelTestSession.DEFAULT_GENERATION_POLICY_VERSION,
                LevelTestQuestionService.MODEL_CONFIG_VERSION,
                diversityContext.exactContentHashes90d()
        ).orElse(null);
    }

    private boolean canPrefetch(
            LevelTestSession session,
            int questionNumber
    ) {
        if (session == null
                || (session.getStatus() != LevelTestSessionStatus.IN_PROGRESS
                && session.getStatus() != LevelTestSessionStatus.EVALUATING)) {
            return false;
        }
        int current = session.currentQuestionNumber();
        return questionNumber == current
                || questionNumber == current + 1;
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
