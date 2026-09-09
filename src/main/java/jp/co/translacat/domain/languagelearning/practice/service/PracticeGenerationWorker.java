package jp.co.translacat.domain.languagelearning.practice.service;

import jp.co.translacat.domain.languagelearning.ai.dto.request.AiPracticeGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiPracticeGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.port.LanguageLearningAiClient;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDifficulty;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Service
public class PracticeGenerationWorker {
    private final PracticePersistenceService persistenceService;
    private final LanguageLearningAiClient aiClient;
    private final Executor executor;
    private final Duration lease;
    private final Set<Long> scheduledSets = ConcurrentHashMap.newKeySet();

    public PracticeGenerationWorker(
            PracticePersistenceService persistenceService,
            LanguageLearningAiClient aiClient,
            @Qualifier("practiceGenerationExecutor") Executor executor,
            @Value("${language-learning.practice.generation-lease-seconds:1800}") long leaseSeconds
    ) {
        this.persistenceService = persistenceService;
        this.aiClient = aiClient;
        this.executor = executor;
        this.lease = Duration.ofSeconds(Math.max(60, leaseSeconds));
    }

    @Scheduled(fixedDelayString = "${language-learning.practice.generation-delay-ms:1000}")
    public void dispatch() {
        for (Long setId : persistenceService.pendingIds(LocalDateTime.now().minus(lease))) {
            schedule(setId);
        }
    }

    private void schedule(Long setId) {
        if (!scheduledSets.add(setId)) return;
        try {
            executor.execute(() -> {
                try {
                    generateNext(setId);
                } catch (RuntimeException error) {
                    // Transaction/connection failures leave durable work reclaimable after its lease.
                    log.error("Practice generation worker failed. setId={}", setId, error);
                } finally {
                    scheduledSets.remove(setId);
                }
            });
        } catch (RejectedExecutionException error) {
            scheduledSets.remove(setId);
            log.debug("Practice generation executor busy; job stays pending. setId={}", setId);
        }
    }

    void generateNext(Long setId) {
        LocalDateTime now = LocalDateTime.now();
        var claimed = persistenceService.claim(setId, now, now.minus(lease));
        if (claimed.isEmpty()) return;
        var claim = claimed.get();
        try {
            // No database transaction is held during the remote AI request.
            var generated = aiClient.generatePractice(claim.request());
            validateGenerated(claim.request(), generated);
            persistenceService.append(claim, generated);
        } catch (RuntimeException error) {
            log.warn("Practice item generation failed. setId={} order={}", setId, claim.order(), error);
            persistenceService.fail(claim, error.getMessage());
        }
        // Each subsequent slot is rediscovered from persisted state by the next scheduler tick.
    }

    static void validateGenerated(
            AiPracticeGenerationRequestDto request, AiPracticeGenerationResponseDto response
    ) {
        if (response == null || response.domain() != request.domain()
                || !Objects.equals(request.requestId(), response.requestId())
                || !Objects.equals(request.mode(), response.mode())
                || response.questions() == null || response.questions().size() != 1
                || response.questions().getFirst() == null) {
            throw invalidResponse();
        }
        var item = response.questions().getFirst();
        PracticeDifficulty expected = request.easierCount() == 1 ? PracticeDifficulty.EASIER
                : request.currentCount() == 1 ? PracticeDifficulty.CURRENT : PracticeDifficulty.CHALLENGE;
        if (item.order() != 1 || item.questionType() == null || item.difficulty() != expected
                || item.prompt() == null || item.prompt().isBlank()
                || item.options() == null || item.options().isEmpty()
                || item.correctAnswer() == null || item.correctAnswer().isEmpty()
                || item.skillTag() == null || item.explanationOrigin() == null
                || item.explanationLearning() == null) {
            throw invalidResponse();
        }
    }

    private static BusinessException invalidResponse() {
        return new BusinessException("Reading/Vocabulary AI 응답 계약이 올바르지 않습니다.",
                LanguageLearningErrorCode.AI_SCHEMA_INVALID);
    }
}
