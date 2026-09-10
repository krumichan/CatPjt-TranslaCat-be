package jp.co.translacat.domain.languagelearning.speaking.evaluation.job.service;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.factory.SpeakingEvaluationRequestFactory;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.entity.SpeakingEvaluationJob;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.event.SpeakingEvaluationJobRequestedEvent;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.model.SpeakingEvaluationJobKey;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.repository.SpeakingEvaluationJobRepository;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionPolicySnapshotService;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.speaking.turn.repository.SpeakingTurnRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Called by submission/retry commands while holding the session lock. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class SpeakingEvaluationJobQueueService {
    private final SpeakingEvaluationJobRepository repository;
    private final SpeakingTurnRepository turnRepository;
    private final SpeakingEvaluationRequestFactory requestFactory;
    private final SpeakingSessionPolicySnapshotService snapshotService;
    private final LanguageLearningJsonCodec jsonCodec;
    private final ApplicationEventPublisher eventPublisher;

    public void enqueue(SpeakingSession session, int problemIndex) {
        requireEnabled(session);
        if (repository.findBySessionIdAndProblemIndex(session.getId(), problemIndex).isPresent()) return;
        AiSpeakingEvaluationRequestDto request = createRequest(session, problemIndex);
        SpeakingEvaluationJob job = repository.save(SpeakingEvaluationJob.pending(
                session, problemIndex, jsonCodec.write(request), LocalDateTime.now()));
        publish(job);
    }

    public int retry(SpeakingSession session, int problemIndex) {
        requireEnabled(session);
        int limit = snapshotService.read(session).manualRetryLimitPerStage();
        SpeakingEvaluationJob job = repository.findOneBySessionIdAndProblemIndex(session.getId(), problemIndex)
                .orElseThrow(() -> invalid("재시도할 평가 작업이 없습니다."));
        if (job.getStatus() == SpeakingEvaluationJob.Status.PENDING
                || job.getStatus() == SpeakingEvaluationJob.Status.RUNNING) return job.getManualRetryCount();
        if (job.getStatus() != SpeakingEvaluationJob.Status.FAILED || job.getManualRetryCount() >= limit)
            throw invalid("Speaking 평가 수동 재시도 한도를 초과했거나 재시도할 수 없는 상태입니다.");
        // Reuse the submitted evidence snapshot. Never re-open recording/editing on a completed session.
        AiSpeakingEvaluationRequestDto previous = jsonCodec.read(job.getRequestJson(), AiSpeakingEvaluationRequestDto.class);
        job.retry(limit, jsonCodec.write(previous.forManualRetry(job.getManualRetryCount() + 1)), LocalDateTime.now());
        publish(job);
        return job.getManualRetryCount();
    }

    private AiSpeakingEvaluationRequestDto createRequest(SpeakingSession session, int problemIndex) {
        if (problemIndex == 0) return requestFactory.create(session,
                turnRepository.findAllBySessionIdOrderByTurnIndexAsc(session.getId()), 0);
        List<SpeakingTurn> attempts = turnRepository.findAllBySessionIdAndProblemIndexOrderByAttemptIndexAsc(
                        session.getId(), problemIndex).stream()
                .filter(turn -> !turn.isExcludedFromEvaluation()).toList();
        String script = problemIndex == 1 ? session.getOpeningAssistantText()
                : turnRepository.findAllBySessionIdAndProblemIndexOrderByAttemptIndexAsc(session.getId(), problemIndex - 1)
                    .stream().map(SpeakingTurn::getAssistantText)
                    .filter(text -> text != null && !text.isBlank()).reduce((first, last) -> last).orElse(null);
        if (script == null || script.isBlank()) throw invalid("듣고 리피트 문제 Script가 없습니다.");
        return requestFactory.createReadAloudProblem(session, problemIndex, attempts, script);
    }

    private void requireEnabled(SpeakingSession session) {
        if (!snapshotService.read(session).speakingEvaluationEnabled())
            throw new BusinessException("Speaking 평가가 비활성화되어 있습니다.", LanguageLearningErrorCode.SPEAKING_DISABLED);
    }

    private void publish(SpeakingEvaluationJob job) {
        eventPublisher.publishEvent(new SpeakingEvaluationJobRequestedEvent(
                new SpeakingEvaluationJobKey(job.getId(), job.getSession().getId())));
    }

    private BusinessException invalid(String message) {
        return new BusinessException(message, LanguageLearningErrorCode.SPEAKING_EVALUATION_FAILED);
    }
}
