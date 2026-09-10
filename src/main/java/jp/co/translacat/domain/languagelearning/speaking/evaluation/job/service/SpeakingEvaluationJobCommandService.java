package jp.co.translacat.domain.languagelearning.speaking.evaluation.job.service;

import jp.co.translacat.domain.languagelearning.activity.repository.LearningActivityRepository;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.entity.SpeakingEvaluationJob;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.model.SpeakingEvaluationClaim;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.model.SpeakingEvaluationJobKey;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.repository.SpeakingEvaluationJobRepository;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.entity.SpeakingReadAloudProblemEvaluation;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.repository.SpeakingReadAloudProblemEvaluationRepository;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.service.SpeakingEvaluationResultCommandService;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.validator.SpeakingEvaluationResponseValidator;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.repository.SpeakingSessionRepository;
import jp.co.translacat.domain.languagelearning.speaking.usage.service.SpeakingAiUsageCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/** Every mutation uses session -> job locks. No external I/O takes place inside these transactions. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class SpeakingEvaluationJobCommandService {
    private final SpeakingSessionRepository sessionRepository;
    private final SpeakingEvaluationJobRepository jobRepository;
    private final SpeakingReadAloudProblemEvaluationRepository problemRepository;
    private final LearningActivityRepository activityRepository;
    private final SpeakingEvaluationResultCommandService resultCommandService;
    private final SpeakingEvaluationResponseValidator responseValidator;
    private final SpeakingAiUsageCommandService usageService;
    private final LanguageLearningJsonCodec jsonCodec;

    @Value("${language-learning.speaking.evaluation-job.lease-seconds:900}")
    private long leaseSeconds = 900;
    @Value("${language-learning.speaking.evaluation-job.max-recoveries:2}")
    private int maxRecoveries = 2;

    public Optional<SpeakingEvaluationClaim> claim(SpeakingEvaluationJobKey key) {
        var locked = lock(key);
        if (locked.isEmpty()) return Optional.empty();
        var state = locked.get();
        SpeakingEvaluationJob job = state.job();
        String token = job.claim(LocalDateTime.now(), Duration.ofSeconds(Math.max(1, leaseSeconds)),
                Math.max(0, maxRecoveries));
        if (token == null) {
            if (job.getStatus() == SpeakingEvaluationJob.Status.FAILED) markFailed(state);
            return Optional.empty();
        }
        AiSpeakingEvaluationRequestDto request;
        try {
            request = jsonCodec.read(job.getRequestJson(), AiSpeakingEvaluationRequestDto.class);
            if (request == null) throw new IllegalStateException("Missing evaluation snapshot");
        } catch (RuntimeException malformedSnapshot) {
            job.fail(token, "EVALUATION_SNAPSHOT_INVALID");
            markFailed(state);
            return Optional.empty();
        }
        if (job.getProblemIndex() == 0) {
            state.session().markEvaluating();
            activityRepository.findBySourceAndReferenceId(LearningSource.SPEAKING,
                    String.valueOf(state.session().getId())).orElseThrow().markEvaluating();
        } else {
            problem(state).markEvaluating();
        }
        return Optional.of(new SpeakingEvaluationClaim(key, job.getProblemIndex(), token,
                job.getManualRetryCount(), request));
    }

    /** Validation, result/metric/profile writes and job completion either all commit or all roll back. */
    public boolean complete(SpeakingEvaluationClaim claim, AiSpeakingEvaluationResponseDto response) {
        var locked = lock(claim.key());
        if (locked.isEmpty() || !locked.get().job().owns(claim.token())) return false;
        var state = locked.get();
        responseValidator.validate(response, claim.request());
        if (state.job().getProblemIndex() == 0) {
            resultCommandService.apply(state.session(), response);
        } else {
            problem(state).markEvaluated(response.status().toUpperCase(java.util.Locale.ROOT),
                    response.overallScore(), response.evaluationConfidence(), jsonCodec.write(response.metrics()),
                    jsonCodec.write(response.strengths()), jsonCodec.write(response.improvements()),
                    jsonCodec.write(response.pronunciationPractice()));
        }
        usageService.record(state.session(), null, response.usage(), claim.manualRetryAttempt());
        state.job().succeed(claim.token());
        return true;
    }

    /** Invoked only AFTER the failed apply transaction has rolled back. */
    public void fail(SpeakingEvaluationClaim claim) {
        var locked = lock(claim.key());
        if (locked.isEmpty()) return;
        var state = locked.get();
        if (state.job().fail(claim.token(), "SPEAKING_EVALUATION_FAILED")) markFailed(state);
    }

    public void release(SpeakingEvaluationClaim claim) {
        var locked = lock(claim.key());
        if (locked.isEmpty()) return;
        var state = locked.get();
        if (!state.job().release(claim.token(), LocalDateTime.now().plusSeconds(5))) return;
        if (state.job().getProblemIndex() == 0) state.session().markEvaluationPending();
        else problem(state).markPending();
    }

    private Optional<LockedJob> lock(SpeakingEvaluationJobKey key) {
        var session = sessionRepository.findOneById(key.sessionId());
        if (session.isEmpty()) return Optional.empty();
        return jobRepository.findOneByIdAndSessionId(key.jobId(), key.sessionId())
                .map(job -> new LockedJob(session.get(), job));
    }

    private SpeakingReadAloudProblemEvaluation problem(LockedJob state) {
        return problemRepository.findBySessionIdAndProblemIndex(state.session().getId(), state.job().getProblemIndex())
                .orElseThrow();
    }

    private void markFailed(LockedJob state) {
        if (state.job().getProblemIndex() == 0) {
            state.session().markEvaluationFailed();
            activityRepository.findBySourceAndReferenceId(LearningSource.SPEAKING,
                    String.valueOf(state.session().getId())).orElseThrow().markEvaluationFailed();
        } else {
            problem(state).markFailed("Speaking 평가 처리에 실패했습니다. 다시 시도해 주세요.");
        }
    }

    private record LockedJob(SpeakingSession session, SpeakingEvaluationJob job) { }
}
