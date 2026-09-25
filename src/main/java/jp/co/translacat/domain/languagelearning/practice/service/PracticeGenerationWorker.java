package jp.co.translacat.domain.languagelearning.practice.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.*;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiPracticeGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiPracticeGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.port.LanguageLearningAiClient;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeQuestionType;
import jp.co.translacat.domain.languagelearning.practice.policy.PracticeAvailabilityPolicy;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.AiServerCommunicationException;
import jp.co.translacat.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
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
    private final int automaticRetryLimit;
    private final Duration retryInitialDelay;
    private final Duration retryMaxDelay;
    private final Set<Long> scheduledSets = ConcurrentHashMap.newKeySet();

    public PracticeGenerationWorker(
            PracticePersistenceService persistenceService,
            LanguageLearningAiClient aiClient,
            @Qualifier("practiceGenerationExecutor") Executor executor,
            @Value("${language-learning.practice.generation-lease-seconds:1800}") long leaseSeconds,
            @Value("${language-learning.practice.automatic-retry-limit:3}") int automaticRetryLimit,
            @Value("${language-learning.practice.retry-initial-delay-seconds:10}") long retryInitialDelaySeconds,
            @Value("${language-learning.practice.retry-max-delay-seconds:120}") long retryMaxDelaySeconds
    ) {
        this.persistenceService = persistenceService;
        this.aiClient = aiClient;
        this.executor = executor;
        this.lease = Duration.ofSeconds(Math.max(60, leaseSeconds));
        this.automaticRetryLimit = Math.max(0, automaticRetryLimit);
        this.retryInitialDelay = Duration.ofSeconds(Math.max(1, retryInitialDelaySeconds));
        this.retryMaxDelay = Duration.ofSeconds(Math.max(
                this.retryInitialDelay.toSeconds(), retryMaxDelaySeconds
        ));
    }

    @Scheduled(fixedDelayString = "${language-learning.practice.generation-delay-ms:1000}")
    public void dispatch() {
        LocalDateTime now = LocalDateTime.now();
        for (Long setId : persistenceService.pendingIds(now, now.minus(lease))) {
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
        var claimed = persistenceService.claim(
                setId, now, now.minus(lease), automaticRetryLimit
        );
        if (claimed.isEmpty()) return;
        var claim = claimed.get();
        if (PracticeAvailabilityPolicy.needsAnyNewB5Structure(
                claim.request(), claim.request().readingSlotTargets(), claim.order())) {
            persistenceService.fail(claim, PracticeAvailabilityPolicy.B5_STRUCTURE_DEFERRED);
            return;
        }
        if (!PracticeAvailabilityPolicy.generationAllowed(claim.request().domain())) {
            // Defense in depth for an already-issued claim; fail() checks its token.
            persistenceService.fail(claim, PracticeAvailabilityPolicy.VOCABULARY_RETIRED);
            return;
        }
        generateClaim(claim);
    }

    private void generateClaim(PracticePersistenceService.GenerationClaim claim) {
        Long setId = claim.setId();
        String stage = "CLAIMED";
        String activeRequestId = claim.request().requestId();
        try {
            // No database transaction is held during the remote AI request.
            AiPracticeGenerationRequestDto generationRequest = claim.request();
            ReadingPassageBundleDto persistedReading = readingBundleFor(generationRequest, claim.order());
            if (persistedReading != null) {
                stage = "READING_BUNDLE_REUSED";
                AiPracticeGenerationResponseDto generated = readingItemResponse(
                        generationRequest, persistedReading, claim.order());
                validateGenerated(generationRequest, generated);
                persistenceService.append(claim, generated);
                return;
            }
            if (isContextualChoice(generationRequest)
                    && generationRequest.vocabularyPlan() == null) {
                AiPracticeGenerationRequestDto planRequest = planOnlyRequest(generationRequest);
                activeRequestId = planRequest.requestId();
                stage = "PLAN_REQUEST_STARTED";
                log.info("Practice generation stage. setId={} order={} requestId={} stage={}",
                        setId, claim.order(), planRequest.requestId(), stage);
                var planned = aiClient.generatePractice(planRequest);
                stage = "PLAN_RESPONSE_RECEIVED";
                log.info("Practice generation stage. setId={} order={} requestId={} stage={}",
                        setId, claim.order(), planRequest.requestId(), stage);
                validateContextualChoicePlanResponse(planRequest, planned);
                if (!persistenceService.persistVocabularyPlan(
                        claim, planned.vocabularyPlan())) {
                    return;
                }
                stage = "PLAN_PERSISTED";
                log.info("Practice generation stage. setId={} order={} requestId={} stage={}",
                        setId, claim.order(), planRequest.requestId(), stage);
                generationRequest = contextRequest(generationRequest, planned.vocabularyPlan());
            }
            if (isContextualChoice(generationRequest)) {
                stage = "LEXICAL_VALIDATION_REQUEST_STARTED";
                log.info("Practice generation stage. setId={} order={} requestId={} stage={} requestPhase=JIT_CONTEXT",
                        setId, claim.order(), generationRequest.requestId(), stage);
            }
            stage = "CONTEXT_REQUEST_STARTED";
            activeRequestId = generationRequest.requestId();
            log.info("Practice generation stage. setId={} order={} requestId={} stage={}",
                    setId, claim.order(), generationRequest.requestId(), stage);
            var generated = aiClient.generatePractice(generationRequest);
            validateGenerated(generationRequest, generated);
            if (isNewReadingBundleRequest(generationRequest)) {
                stage = "READING_BUNDLE_VALIDATED";
                if (!persistenceService.persistReadingBundle(claim, generated.readingBundle())) return;
                stage = "READING_BUNDLE_PERSISTED";
            }
            if (isContextualChoice(generationRequest)
                    && !generationRequest.vocabularyPlan().items().get(claim.order() - 1)
                    .equals(generated.vocabularyPlan().items().get(claim.order() - 1))) {
                stage = "PLAN_ITEM_REPAIRED";
                log.info("Practice generation stage. setId={} order={} requestId={} stage={}",
                        setId, claim.order(), generationRequest.requestId(), stage);
            }
            persistenceService.append(claim, generated);
        } catch (AiServerCommunicationException error) {
            int previousQuestionCount = claim.request().previousQuestions() == null
                    ? 0 : claim.request().previousQuestions().size();
            log.warn(
                    "Practice generation stage. setId={} order={} requestId={} stage=GENERATION_FAILED "
                            + "failedStage={} endpoint={} "
                            + "httpStatus={} safeDetail={} previousQuestions={} questionOffset={} "
                            + "failureCode={} retryable={}",
                    setId,
                    claim.order(),
                    activeRequestId,
                    stage,
                    "/api/v1/language-learning/practice/generate",
                    error.getHttpStatus(),
                    error.getSafeDetail(),
                    previousQuestionCount,
                    previousQuestionCount,
                    error.getErrorCode(),
                    error.isRetryable()
            );
            if (error.isRetryable()) {
                persistenceService.recordInfrastructureFailure(
                        claim,
                        safeCode(error.getErrorCode()),
                        LocalDateTime.now().plus(retryDelay(claim.infrastructureRetryCount())),
                        automaticRetryLimit
                );
            } else {
                persistenceService.fail(claim, safeCode(error.getErrorCode()));
            }
        } catch (BusinessException error) {
            log.warn(
                    "Practice generation stage. setId={} order={} requestId={} stage=GENERATION_FAILED "
                            + "failedStage={} failureCode={} httpStatus=NA safeDetail=NA retryable=false",
                    setId, claim.order(), activeRequestId, stage, error.getErrorCode()
            );
            persistenceService.fail(claim, safeCode(error.getErrorCode()));
        } catch (RuntimeException error) {
            log.warn("Practice generation stage. setId={} order={} requestId={} stage=GENERATION_FAILED "
                            + "failedStage={} failureCode=UNKNOWN httpStatus=NA safeDetail=NA retryable=false type={}",
                    setId, claim.order(), activeRequestId, stage, error.getClass().getSimpleName());
            persistenceService.fail(claim, "UNKNOWN");
        }
        // Each subsequent slot is rediscovered from persisted state by the next scheduler tick.
    }

    Duration retryDelay(int retryCount) {
        long multiplier = 1L << Math.min(Math.max(0, retryCount), 30);
        long seconds;
        try {
            seconds = Math.multiplyExact(retryInitialDelay.toSeconds(), multiplier);
        } catch (ArithmeticException ignored) {
            seconds = retryMaxDelay.toSeconds();
        }
        return Duration.ofSeconds(Math.min(seconds, retryMaxDelay.toSeconds()));
    }

    private String safeCode(String code) {
        return code == null || code.isBlank() ? "UNKNOWN" : code;
    }

    static void validateGenerated(
            AiPracticeGenerationRequestDto request, AiPracticeGenerationResponseDto response
    ) {
        if (response == null || response.domain() != request.domain()
                || !Objects.equals(request.requestId(), response.requestId())
                || !Objects.equals(request.mode(), response.mode())
                || response.questions() == null || response.questions().size() !=
                (isNewReadingBundleRequest(request) ? request.questionCount() : 1)
                || response.questions().getFirst() == null) {
            throw invalidResponse();
        }
        if (isNewReadingBundleRequest(request)) {
            validateReadingBundle(request, response);
            return;
        }
        if (response.readingBundle() != null) throw invalidResponse();
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
        if (isContextualChoice(request)) {
            validateVocabularyPlan(request, response.vocabularyPlan());
            validateContextualChoicePlanDelta(request, response.vocabularyPlan());
            validateContextualChoiceQuestion(request, item, response.vocabularyPlan());
        } else if (response.vocabularyPlan() != null) {
            throw invalidResponse();
        }
    }

    private static boolean isNewReadingBundleRequest(AiPracticeGenerationRequestDto request) {
        return request.domain() == PracticeDomain.READING
                && (request.questionCount() == 2 || request.questionCount() == 3);
    }

    private static ReadingPassageBundleDto readingBundleFor(
            AiPracticeGenerationRequestDto request, int globalOrder
    ) {
        if (request.domain() != PracticeDomain.READING || request.readingBundles() == null) return null;
        return request.readingBundles().get(globalOrder <= 3 ? "p1" : "p2");
    }

    private static AiPracticeGenerationResponseDto readingItemResponse(
            AiPracticeGenerationRequestDto request, ReadingPassageBundleDto bundle, int globalOrder
    ) {
        if (request.questionCount() > 1) {
            // Reuse the already-verified private bundle after an interrupted append,
            // preserving one atomic 3/2-row publication without an AI call.
            return new AiPracticeGenerationResponseDto(
                    request.requestId(), bundle.promptVersion(), request.domain(), request.mode(),
                    request.complexityBand(), bundle.questions(), null, bundle);
        }
        int index = globalOrder <= 3 ? globalOrder - 1 : globalOrder - 4;
        if (bundle.questions() == null || index >= bundle.questions().size()) throw invalidResponse();
        PracticeGeneratedQuestionDto item = withOrder(bundle.questions().get(index), 1);
        return new AiPracticeGenerationResponseDto(
                request.requestId(), bundle.promptVersion(), request.domain(), request.mode(),
                request.complexityBand(), List.of(item));
    }

    private static PracticeGeneratedQuestionDto withOrder(PracticeGeneratedQuestionDto item, int order) {
        if (item == null) throw invalidResponse();
        return new PracticeGeneratedQuestionDto(order, item.questionType(), item.difficulty(),
                item.complexityBand(), item.passageId(), item.passageText(), item.prompt(),
                item.options(), item.correctAnswer(), item.skillTag(), item.evidenceText(),
                item.explanationOrigin(), item.explanationLearning(), item.targetExpression(),
                item.canonicalKey(), item.reviewTarget(), item.vocabularyCandidates());
    }

    static void validateReadingBundle(
            AiPracticeGenerationRequestDto request, AiPracticeGenerationResponseDto response
    ) {
        ReadingPassageBundleDto bundle = response.readingBundle();
        int start = request.previousQuestions() == null ? 1 : request.previousQuestions().size() + 1;
        String passageId = start == 1 ? "p1" : start == 4 ? "p2" : null;
        if (passageId == null || request.readingSlotTargets() == null
                || request.readingSlotTargets().size() != 5 || bundle == null
                || !passageId.equals(bundle.passageId()) || bundle.questionPlans() == null
                || isBlank(bundle.promptVersion())
                || !bundle.promptVersion().equals(response.promptVersion())
                || bundle.questionPlans().size() != request.questionCount()
                || bundle.questions() == null || !bundle.questions().equals(response.questions())
                || response.vocabularyPlan() != null) throw invalidResponse();
        String passageText = null;
        Set<String> focuses = new HashSet<>();
        for (int index = 0; index < response.questions().size(); index++) {
            int globalOrder = start + index;
            var item = response.questions().get(index);
            var plan = bundle.questionPlans().get(index);
            ReadingSlotTargetDto target = request.readingSlotTargets().get(globalOrder - 1);
            if (item == null || plan == null || target == null
                    || target.globalOrder() != globalOrder || plan.globalOrder() != globalOrder
                    || item.order() != index + 1 || item.questionType() != PracticeQuestionType.SINGLE_CHOICE
                    || item.difficulty() != target.difficulty()
                    || item.complexityBand() != target.complexityBand()
                    || !Objects.equals(item.skillTag(), target.skillTag())
                    || plan.difficulty() != target.difficulty()
                    || plan.complexityBand() != target.complexityBand()
                    || !Objects.equals(plan.skillTag(), target.skillTag())
                    || !passageId.equals(item.passageId()) || isBlank(item.passageText())
                    || isBlank(item.prompt()) || isBlank(item.explanationOrigin())
                    || isBlank(item.explanationLearning()) || isBlank(plan.clueQuote())
                    || isBlank(plan.questionFocus()) || !item.passageText().contains(plan.clueQuote())
                    || !focuses.add(normalized(plan.questionFocus()))
                    || item.options() == null || item.options().size() != 4
                    || item.correctAnswer() == null || item.correctAnswer().size() != 1) {
                throw invalidResponse();
            }
            if (passageText == null) passageText = item.passageText();
            else if (!passageText.equals(item.passageText())) throw invalidResponse();
        }
        if (!Objects.equals(sha256(passageText), bundle.passageSha256())) throw invalidResponse();
    }

    static void validateReadingAcceptedPrefix(
            Map<String, ReadingPassageBundleDto> bundles,
            List<PracticeGeneratedQuestionDto> accepted
    ) {
        if (bundles == null || bundles.keySet().stream().anyMatch(key -> !Set.of("p1", "p2").contains(key))) {
            throw invalidResponse();
        }
        for (int index = 0; index < accepted.size(); index++) {
            int globalOrder = index + 1;
            ReadingPassageBundleDto bundle = bundles.get(globalOrder <= 3 ? "p1" : "p2");
            if (bundle == null) continue; // Legacy accepted rows may predate this private bundle contract.
            int localIndex = globalOrder <= 3 ? index : index - 3;
            if (bundle.questions() == null || localIndex >= bundle.questions().size()
                    || !withOrder(bundle.questions().get(localIndex), globalOrder).equals(accepted.get(index))) {
                throw invalidResponse();
            }
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static void validateContextualChoicePlanResponse(
            AiPracticeGenerationRequestDto request,
            AiPracticeGenerationResponseDto response
    ) {
        if (response == null || response.domain() != request.domain()
                || !Objects.equals(request.requestId(), response.requestId())
                || !Objects.equals(request.mode(), response.mode())
                || (request.vocabularyPlanOnly()
                && response.questions() != null && !response.questions().isEmpty())) {
            throw invalidResponse();
        }
        validateVocabularyPlan(request, response.vocabularyPlan());
    }

    private static AiPracticeGenerationRequestDto planOnlyRequest(
            AiPracticeGenerationRequestDto request
    ) {
        return copyContextualChoiceRequest(
                request, request.requestId(), null, true
        );
    }

    private static AiPracticeGenerationRequestDto contextRequest(
            AiPracticeGenerationRequestDto request,
            PersonalizedVocabularyPlanDto plan
    ) {
        return copyContextualChoiceRequest(
                request, request.requestId() + "-context", plan, false
        );
    }

    private static AiPracticeGenerationRequestDto copyContextualChoiceRequest(
            AiPracticeGenerationRequestDto request,
            String requestId,
            PersonalizedVocabularyPlanDto plan,
            boolean planOnly
    ) {
        return new AiPracticeGenerationRequestDto(
                requestId, request.domain(), request.mode(), request.originLanguage(),
                request.learningLanguage(), request.questionCount(), request.complexityBand(),
                request.easierCount(), request.currentCount(), request.challengeCount(),
                request.selectedKeywords(), request.weakSignals(), request.recentMistakes(),
                request.reviewTargets(), request.reviewQuestionCount(), request.generationDate(),
                request.previousQuestions(), plan, planOnly
        );
    }

    private static void validateContextualChoiceQuestion(
            AiPracticeGenerationRequestDto request,
            PracticeGeneratedQuestionDto question,
            PersonalizedVocabularyPlanDto plan
    ) {
        int globalOrder = (request.previousQuestions() == null ? 0 : request.previousQuestions().size()) + 1;
        validateContextualChoiceQuestionAt(globalOrder, question, plan);
    }

    static void validateContextualChoiceAcceptedPrefix(
            PersonalizedVocabularyPlanDto plan,
            List<PracticeGeneratedQuestionDto> accepted
    ) {
        for (int index = 0; index < accepted.size(); index++) {
            PracticeGeneratedQuestionDto question = accepted.get(index);
            if (question == null || question.order() != index + 1) throw invalidResponse();
            validateContextualChoiceQuestionAt(index + 1, question, plan);
        }
    }

    static void validateContextualChoicePlanDelta(
            AiPracticeGenerationRequestDto request,
            PersonalizedVocabularyPlanDto returned
    ) {
        PersonalizedVocabularyPlanDto original = request.vocabularyPlan();
        if (original == null) return;
        if (returned == null || !Objects.equals(original.version(), returned.version())
                || original.items() == null || returned.items() == null
                || original.items().size() != returned.items().size()) {
            throw invalidResponse();
        }
        int currentOrder = (request.previousQuestions() == null ? 0 : request.previousQuestions().size()) + 1;
        for (int index = 0; index < original.items().size(); index++) {
            VocabularyPlanItemDto before = original.items().get(index);
            VocabularyPlanItemDto after = returned.items().get(index);
            if (before == null || after == null) throw invalidResponse();
            if (index + 1 != currentOrder) {
                if (!before.equals(after)) throw invalidResponse();
                continue;
            }
            if (before.globalOrder() != after.globalOrder()
                    || before.reviewTarget() != after.reviewTarget()
                    || !Objects.equals(before.skillTag(), after.skillTag())
                    || before.difficulty() != after.difficulty()
                    || before.complexityBand() != after.complexityBand()
                    || !Objects.equals(before.scenarioFamily(), after.scenarioFamily())
                    || !Objects.equals(before.anchorType(), after.anchorType())
                    || !Objects.equals(before.anchorValue(), after.anchorValue())) {
                throw invalidResponse();
            }
            if (before.reviewTarget()
                    && (!Objects.equals(before.targetExpression(), after.targetExpression())
                    || !Objects.equals(before.canonicalKey(), after.canonicalKey()))) {
                throw invalidResponse();
            }
            if (!before.reviewTarget()
                    && Objects.equals(before.targetExpression(), after.targetExpression())
                    && !Objects.equals(before.canonicalKey(), after.canonicalKey())) {
                throw invalidResponse();
            }
        }
        validateContextualChoiceAcceptedPrefix(original,
                request.previousQuestions() == null ? List.of() : request.previousQuestions());
    }

    private static void validateContextualChoiceQuestionAt(
            int globalOrder,
            PracticeGeneratedQuestionDto question,
            PersonalizedVocabularyPlanDto plan
    ) {
        if (globalOrder > plan.items().size()) throw invalidResponse();
        VocabularyPlanItemDto planned = plan.items().get(globalOrder - 1);
        String correctKey = "ABCD".substring((globalOrder - 1) % 4, (globalOrder - 1) % 4 + 1);
        if (question.questionType() != PracticeQuestionType.SINGLE_CHOICE
                || !Objects.equals(question.targetExpression(), planned.targetExpression())
                || !Objects.equals(question.canonicalKey(), planned.canonicalKey())
                || !Objects.equals(question.skillTag(), planned.skillTag())
                || question.difficulty() != planned.difficulty()
                || question.complexityBand() != planned.complexityBand()
                || question.reviewTarget() != planned.reviewTarget()
                || question.options() == null || question.options().size() != 4
                || question.correctAnswer() == null || question.correctAnswer().size() != 1
                || !correctKey.equals(question.correctAnswer().getFirst())) {
            throw invalidResponse();
        }
        Map<String, String> optionsByKey = new HashMap<>();
        Set<String> optionTexts = new HashSet<>();
        for (var option : question.options()) {
            if (option == null || isBlank(option.key()) || isBlank(option.text())
                    || optionsByKey.put(option.key(), option.text()) != null
                    || !optionTexts.add(normalized(option.text()))) {
                throw invalidResponse();
            }
        }
        Set<String> plannedTexts = new HashSet<>();
        plannedTexts.add(normalized(planned.targetExpression()));
        planned.distractors().stream().map(PracticeGenerationWorker::normalized)
                .forEach(plannedTexts::add);
        if (!optionTexts.equals(plannedTexts)
                || !Objects.equals(optionsByKey.get(correctKey), planned.targetExpression())) {
            throw invalidResponse();
        }
        var expectedTexts = new java.util.ArrayList<>(planned.distractors());
        expectedTexts.add((globalOrder - 1) % 4, planned.targetExpression());
        for (int index = 0; index < 4; index++) {
            var option = question.options().get(index);
            if (!"ABCD".substring(index, index + 1).equals(option.key())
                    || !expectedTexts.get(index).equals(option.text())) {
                throw invalidResponse();
            }
        }
    }

    private static void validateVocabularyPlan(
            AiPracticeGenerationRequestDto request,
            PersonalizedVocabularyPlanDto plan
    ) {
        if (plan == null || !"personalized-daily-vocabulary-plan-v1".equals(plan.version())
                || plan.items() == null || plan.items().size() != 10) {
            throw invalidResponse();
        }
        Map<PracticeDifficulty, Integer> difficulties = new HashMap<>();
        Map<String, Integer> skills = new HashMap<>();
        Set<String> targets = new HashSet<>();
        Set<String> canonicalKeys = new HashSet<>();
        int reviewCount = 0;
        for (int index = 0; index < plan.items().size(); index++) {
            VocabularyPlanItemDto item = plan.items().get(index);
            if (item == null || item.globalOrder() != index + 1
                    || isBlank(item.targetExpression()) || isBlank(item.canonicalKey())
                    || item.difficulty() == null || item.complexityBand() != expectedBand(
                    request.complexityBand(), item.difficulty())
                    || !Set.of("MEANING", "COLLOCATION", "NUANCE", "REGISTER", "PRAGMATIC_FIT")
                    .contains(item.skillTag())
                    || item.distractors() == null || item.distractors().size() != 3) {
                throw invalidResponse();
            }
            if (item.reviewTarget()) {
                reviewCount++;
                if (item.globalOrder() != reviewCount || item.scenarioFamily() != null
                        || item.anchorType() != null || item.anchorValue() != null
                        || request.reviewTargets() == null
                        || request.reviewTargets().stream().noneMatch(review ->
                        Objects.equals(review.canonicalKey(), item.canonicalKey())
                                && Objects.equals(review.expression(), item.targetExpression())
                                && Objects.equals(
                                review.preferredSkill() == null ? "MEANING" : review.preferredSkill(),
                                item.skillTag()
                        ))) {
                    throw invalidResponse();
                }
            } else if (isBlank(item.scenarioFamily()) || isBlank(item.anchorType())
                    || isBlank(item.anchorValue()) || !validAnchor(request, item)) {
                throw invalidResponse();
            }
            String target = normalized(item.targetExpression());
            String canonical = normalized(item.canonicalKey());
            if (!targets.add(target) || !canonicalKeys.add(canonical)) throw invalidResponse();
            Set<String> distractors = new HashSet<>();
            for (String distractor : item.distractors()) {
                String normalizedDistractor = normalized(distractor);
                if (normalizedDistractor.isBlank() || normalizedDistractor.equals(target)
                        || !distractors.add(normalizedDistractor)) {
                    throw invalidResponse();
                }
            }
            difficulties.merge(item.difficulty(), 1, Integer::sum);
            skills.merge(item.skillTag(), 1, Integer::sum);
        }
        Map<PracticeDifficulty, Integer> expectedDifficulties = Map.of(
                PracticeDifficulty.EASIER, 2,
                PracticeDifficulty.CURRENT, 6,
                PracticeDifficulty.CHALLENGE, 2
        );
        Map<String, Integer> expectedSkills = Map.of(
                "MEANING", 2,
                "COLLOCATION", 2,
                "NUANCE", 2,
                "REGISTER", 2,
                "PRAGMATIC_FIT", 2
        );
        if (reviewCount > 2 || !difficulties.equals(expectedDifficulties)
                || !skills.equals(expectedSkills)) {
            throw invalidResponse();
        }
    }

    private static boolean validAnchor(
            AiPracticeGenerationRequestDto request,
            VocabularyPlanItemDto item
    ) {
        return switch (item.anchorType()) {
            case "SELECTED_KEYWORD" -> request.selectedKeywords() != null
                    && request.selectedKeywords().contains(item.anchorValue());
            case "WEAK_SIGNAL" -> request.weakSignals() != null
                    && request.weakSignals().contains(item.anchorValue());
            case "RECENT_MISTAKE" -> request.recentMistakes() != null
                    && request.recentMistakes().contains(item.anchorValue());
            case "LEARNING_PROFILE" -> Objects.equals(
                    request.learningLanguage(), item.anchorValue())
                    && (request.selectedKeywords() == null || request.selectedKeywords().isEmpty())
                    && (request.weakSignals() == null || request.weakSignals().isEmpty())
                    && (request.recentMistakes() == null || request.recentMistakes().isEmpty());
            default -> false;
        };
    }

    private static int expectedBand(int currentBand, PracticeDifficulty difficulty) {
        return switch (difficulty) {
            case EASIER -> Math.max(1, currentBand - 1);
            case CURRENT -> currentBand;
            case CHALLENGE -> Math.min(5, currentBand + 1);
        };
    }

    private static boolean isContextualChoice(AiPracticeGenerationRequestDto request) {
        return request.domain() == PracticeDomain.VOCABULARY
                && "CONTEXTUAL_CHOICE".equals(request.mode());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalized(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
                .strip().toLowerCase(java.util.Locale.ROOT);
    }

    private static BusinessException invalidResponse() {
        return new BusinessException("Reading/Vocabulary AI 응답 계약이 올바르지 않습니다.",
                LanguageLearningErrorCode.AI_SCHEMA_INVALID);
    }
}
