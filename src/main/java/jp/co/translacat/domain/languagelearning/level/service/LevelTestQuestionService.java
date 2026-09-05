package jp.co.translacat.domain.languagelearning.level.service;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestOptionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestPreviousResultDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestReferenceAudioUploadDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiLevelTestQuestionRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiLevelTestQuestionResponseDto;
import jp.co.translacat.domain.languagelearning.ai.port.LanguageLearningAiClient;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestAnswerMode;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionStatus;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelQuestionResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestOptionResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestTaskGuidanceResponseDto;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestEvaluation;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestResponse;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;
import jp.co.translacat.domain.languagelearning.level.policy.LevelTestQuestionContentPolicy;
import jp.co.translacat.domain.languagelearning.level.policy.LevelTestRecipe;
import jp.co.translacat.domain.languagelearning.level.policy.LevelTestScenarioBalancePolicy;
import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionCandidateStatus;
import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionPool;
import jp.co.translacat.domain.languagelearning.level.pool.policy.LevelTestQuestionPoolPolicy;
import jp.co.translacat.domain.languagelearning.level.pool.audio.service.LevelTestReferenceAudioUploadService;
import jp.co.translacat.domain.languagelearning.level.pool.service.LevelTestQuestionCandidateCommandService;
import jp.co.translacat.domain.languagelearning.level.pool.service.LevelTestQuestionCandidateQueryService;
import jp.co.translacat.domain.languagelearning.level.pool.service.LevelTestQuestionPoolCommandService;
import jp.co.translacat.domain.languagelearning.level.pool.service.LevelTestQuestionPoolQueryService;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestEvaluationRepository;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestItemRepository;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestResponseRepository;
import jp.co.translacat.domain.languagelearning.listening.audio.model.ListeningAudioObject;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityContext;
import jp.co.translacat.domain.languagelearning.quality.service.GenerationDiversityContextService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LevelTestQuestionService {

    public static final String MODEL_CONFIG_VERSION =
            "level-test-model-config-v1";

    private final LevelTestItemRepository itemRepository;
    private final LevelTestResponseRepository responseRepository;
    private final LevelTestEvaluationRepository evaluationRepository;
    private final LanguageLearningAiClient aiClient;
    private final LevelTestRecipe recipe;
    private final LevelTestQuestionContentPolicy contentPolicy;
    private final LevelTestScenarioBalancePolicy scenarioBalancePolicy;
    private final GenerationDiversityContextService diversityContextService;
    private final LevelTestQuestionPersistenceService persistenceService;
    private final LevelTestReferenceAudioService referenceAudioService;
    private final LevelTestQuestionPoolQueryService poolQueryService;
    private final LevelTestQuestionPoolCommandService poolCommandService;
    private final LevelTestQuestionCandidateQueryService candidateQueryService;
    private final LevelTestQuestionCandidateCommandService candidateCommandService;
    private final LevelTestQuestionPoolPolicy poolPolicy;
    private final LevelTestReferenceAudioUploadService audioUploadService;
    private final LanguageLearningJsonCodec jsonCodec;

    @Value("${language-learning.level-test.question-pool.candidate-wait-ms:5000}")
    private long candidateWaitMs;

    public LevelTestItem getOrGenerateCurrent(LevelTestSession session) {
        if (session.getStatus() != LevelTestSessionStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "Level Test가 문제 응답 가능한 상태가 아닙니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }

        int questionNumber = session.currentQuestionNumber();
        if (questionNumber < 1
                || questionNumber > LevelTestRecipe.TOTAL_QUESTIONS) {
            throw new BusinessException(
                    "Level Test가 완료 단계입니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }

        long startedAt = System.nanoTime();
        LevelTestItem existing = itemRepository
                .findBySessionIdAndQuestionNumber(
                        session.getId(),
                        questionNumber
                )
                .orElse(null);
        if (existing != null) {
            LevelTestQuestionContentPolicy.Health health = contentPolicy.inspect(existing);
            if (!health.valid()) {
                if (existing.getPoolQuestionId() != null) {
                    quarantinePoolQuestion(
                            existing.getPoolQuestionId(),
                            health.reason(),
                            "CURRENT_ITEM"
                    );
                }
                boolean discarded = persistenceService.discardReadyInvalidItem(
                        existing.getId()
                );
                if (!discarded) {
                    throw invalidContract();
                }
                log.warn(
                        "Invalid current Level Test item discarded before response. sessionId={}, itemId={}, questionNumber={}, reason={}",
                        session.getId(),
                        existing.getId(),
                        questionNumber,
                        health.reason()
                );
                existing = null;
            }
        }
        Resolution resolution = existing == null
                ? resolve(session, questionNumber)
                : new Resolution(existing, "EXISTING");

        referenceAudioService.ensureReferenceAudio(resolution.item());
        LevelTestItem item = itemRepository
                .findById(resolution.item().getId())
                .orElseThrow();
        log.info(
                "Level Test question resolved. sessionId={}, questionNumber={}, band={}, source={}, elapsedMs={}",
                session.getId(),
                questionNumber,
                item.getComplexityBandValue(),
                resolution.source(),
                elapsedMs(startedAt)
        );
        return item;
    }

    public LevelQuestionResponseDto toResponse(LevelTestItem item) {
        List<LevelTestOptionDto> options = jsonCodec.read(
                item.getOptionsJson(),
                new TypeReference<List<LevelTestOptionDto>>() {
                }
        );
        Map<String, Object> referencePayload = readReferencePayload(item.getReferencePayloadJson());
        String emphasisText = stringValue(referencePayload.get("emphasisText"));
        LevelTestTaskGuidanceResponseDto taskGuidance = taskGuidance(referencePayload);
        String evaluationReasonCode = responseRepository.findByItemId(item.getId())
                .flatMap(response -> evaluationRepository.findByResponseId(response.getId()))
                .map(LevelTestEvaluation::getReasonCode)
                .orElse(null);

        return new LevelQuestionResponseDto(
                item.getSession().getId(),
                item.getSession().getSessionType(),
                item.getId(),
                item.getQuestionNumber(),
                item.getSession().getTotalQuestions(),
                item.getDomain(),
                item.getItemType(),
                item.getComplexityBandValue(),
                item.getInstruction(),
                item.getInstructionLanguage(),
                item.getAnswerMode(),
                item.getAnswerLanguage(),
                promptTextForActiveResponse(item.getItemType(), item.getPromptText()),
                options.stream()
                        .map(value -> new LevelTestOptionResponseDto(
                                value.key(),
                                value.text()
                        ))
                        .toList(),
                emphasisText,
                taskGuidance,
                item.getReferenceAudioObjectKey() != null,
                repeatReferenceTextForActiveResponse(
                        item.getItemType(),
                        item.getQuestionNumber(),
                        stringValue(referencePayload.get("referenceText"))
                ),
                referencePlaybackLimit(item.getItemType(), item.getQuestionNumber()),
                item.getMaxAnswerLength(),
                item.getMaxAudioSeconds(),
                item.getStatus(),
                evaluationReasonCode
        );
    }

    private Map<String, Object> readReferencePayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return jsonCodec.read(json, new TypeReference<Map<String, Object>>() {
        });
    }

    private LevelTestTaskGuidanceResponseDto taskGuidance(Map<String, Object> payload) {
        List<String> facts = stringList(payload.get("providedFacts"));
        List<String> intents = stringList(payload.get("requiredIntents"));
        List<String> constraints = stringList(payload.get("responseConstraints"));
        if (facts.isEmpty() && intents.isEmpty() && constraints.isEmpty()) {
            return null;
        }
        return new LevelTestTaskGuidanceResponseDto(facts, intents, constraints);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isBlank() ? null : text;
    }

    static String promptTextForActiveResponse(
            LevelTestItemType itemType,
            String promptText
    ) {
        if (itemType == LevelTestItemType.GRAMMAR_SENTENCE_ORDER) {
            // promptText is the completed sentence used for scoring/history.
            // Exposing it while the question is active leaks the correct order.
            return "";
        }
        if (itemType == LevelTestItemType.SPEAKING_REPEAT) {
            // The reference audio is the actual task content. Avoid showing a second
            // translated/restated task block beside the concise operation instruction.
            return "";
        }
        return promptText;
    }

    static String repeatReferenceTextForActiveResponse(
            LevelTestItemType itemType,
            int questionNumber,
            String referenceText
    ) {
        if (itemType != LevelTestItemType.SPEAKING_REPEAT || questionNumber != 18) {
            return null;
        }
        return referenceText;
    }

    static Integer referencePlaybackLimit(
            LevelTestItemType itemType,
            int questionNumber
    ) {
        if (itemType != LevelTestItemType.SPEAKING_REPEAT) {
            return null;
        }
        return questionNumber == 19 ? 3 : 2;
    }

    @Transactional(readOnly = true)
    public ListeningAudioObject referenceAudio(
            Long userId,
            Long itemId
    ) {
        LevelTestItem item = itemRepository
                .findByIdAndSessionUserId(itemId, userId)
                .orElseThrow(() -> new BusinessException(
                        "Level Test 문항을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.LEVEL_TEST_NOT_FOUND
                ));
        return referenceAudioService.load(item);
    }

    public LevelTestQuestionPool generatePoolCandidate(
            LevelTestSession session,
            int questionNumber,
            int targetComplexityBand
    ) {
        LevelTestRecipe.Entry expected = recipe.entry(questionNumber);
        DiversityContext diversityContext = diversityContextService
                .levelTestContext(
                        session.getUser().getId(),
                        session.getLearningLanguage(),
                        session.getId()
                );
        List<String> preferredScenarioCategories = scenarioBalancePolicy.preferredForSession(
                diversityContext,
                session.getId(),
                questionNumber,
                expected.domain()
        );
        AiLevelTestQuestionResponseDto response = generateResponse(
                session,
                expected,
                questionNumber,
                targetComplexityBand,
                diversityContext,
                preferredScenarioCategories,
                true
        );
        LevelTestQuestionPool poolQuestion = poolCommandService.register(
                session.getOriginLanguage(),
                session.getLearningLanguage(),
                questionNumber,
                response,
                LevelTestSession.DEFAULT_GENERATION_POLICY_VERSION,
                MODEL_CONFIG_VERSION
        );
        cleanupUnusedReferenceAudio(response, poolQuestion);
        return compatiblePoolQuestion(
                poolQuestion,
                session,
                expected,
                targetComplexityBand,
                List.of(),
                preferredScenarioCategories
        ) ? poolQuestion : null;
    }

    private Resolution resolve(
            LevelTestSession session,
            int questionNumber
    ) {
        LevelTestRecipe.Entry expected = recipe.entry(questionNumber);
        int targetBand = session.currentComplexityBand();
        DiversityContext diversityContext = diversityContextService
                .levelTestContext(
                        session.getUser().getId(),
                        session.getLearningLanguage(),
                        session.getId()
                );
        List<String> excludedHashes = diversityContext.exactContentHashes90d();
        List<String> preferredScenarioCategories = scenarioBalancePolicy.preferredForSession(
                diversityContext,
                session.getId(),
                questionNumber,
                expected.domain()
        );

        Resolution prefetched = resolvePrefetchedCandidate(
                session,
                expected,
                questionNumber,
                targetBand,
                excludedHashes,
                preferredScenarioCategories
        );
        if (prefetched != null) {
            return prefetched;
        }

        LevelTestQuestionPool reusable = findReusablePoolQuestion(
                session,
                expected,
                questionNumber,
                targetBand,
                excludedHashes,
                preferredScenarioCategories
        );
        if (reusable != null) {
            LevelTestItem item = persistenceService.saveFromPool(
                    session,
                    questionNumber,
                    reusable
            );
            candidateCommandService.expireAvailableForQuestion(
                    session.getId(),
                    questionNumber
            );
            return new Resolution(item, "QUESTION_POOL");
        }

        AiLevelTestQuestionResponseDto response;
        try {
            response = generateResponse(
                    session,
                    expected,
                    questionNumber,
                    targetBand,
                    diversityContext,
                    preferredScenarioCategories,
                    false
            );
        } catch (RuntimeException generationFailure) {
            // A background prefetch/refill can finish while the synchronous AI call is
            // running.  Re-check those sources before surfacing an avoidable 4xx/5xx
            // to the learner.  The original failure is rethrown only when no healthy
            // replacement became available.
            Resolution recovered = resolvePrefetchedCandidate(
                    session,
                    expected,
                    questionNumber,
                    targetBand,
                    excludedHashes,
                    preferredScenarioCategories
            );
            if (recovered == null) {
                LevelTestQuestionPool recoveredPool = findReusablePoolQuestion(
                        session,
                        expected,
                        questionNumber,
                        targetBand,
                        excludedHashes,
                        preferredScenarioCategories
                );
                if (recoveredPool == null) {
                    recoveredPool = findReusablePoolQuestion(
                            session,
                            expected,
                            questionNumber,
                            targetBand,
                            excludedHashes,
                            List.of()
                    );
                    if (recoveredPool != null) {
                        log.warn(
                                "Level Test scenario balance relaxed only after synchronous AI failure. sessionId={}, questionNumber={}, band={}, scenarioCategory={}",
                                session.getId(),
                                questionNumber,
                                targetBand,
                                recoveredPool.getScenarioCategory()
                        );
                    }
                }
                if (recoveredPool != null) {
                    LevelTestItem recoveredItem = persistenceService.saveFromPool(
                            session,
                            questionNumber,
                            recoveredPool
                    );
                    candidateCommandService.expireAvailableForQuestion(
                            session.getId(),
                            questionNumber
                    );
                    recovered = new Resolution(recoveredItem, "QUESTION_POOL_AFTER_AI_FAILURE");
                }
            }
            if (recovered != null) {
                log.warn(
                        "Level Test synchronous generation failed but recovered from an available candidate. sessionId={}, questionNumber={}, band={}, source={}, failureType={}",
                        session.getId(),
                        questionNumber,
                        targetBand,
                        recovered.source(),
                        generationFailure.getClass().getSimpleName()
                );
                return recovered;
            }
            throw generationFailure;
        }
        LevelTestQuestionPool registered = poolCommandService.register(
                session.getOriginLanguage(),
                session.getLearningLanguage(),
                questionNumber,
                response,
                LevelTestSession.DEFAULT_GENERATION_POLICY_VERSION,
                MODEL_CONFIG_VERSION
        );
        LevelTestItem item;
        if (compatiblePoolQuestion(
                registered,
                session,
                expected,
                targetBand,
                excludedHashes,
                preferredScenarioCategories
        )) {
            cleanupUnusedReferenceAudio(response, registered);
            item = persistenceService.saveFromPool(
                    session,
                    questionNumber,
                    registered
            );
        } else {
            item = persistenceService.saveGenerated(session, response);
        }
        candidateCommandService.expireAvailableForQuestion(
                session.getId(),
                questionNumber
        );
        return new Resolution(item, "AI_GENERATED");
    }

    private Resolution resolvePrefetchedCandidate(
            LevelTestSession session,
            LevelTestRecipe.Entry expected,
            int questionNumber,
            int targetBand,
            List<String> excludedHashes,
            List<String> preferredScenarioCategories
    ) {
        LevelTestQuestionCandidateQueryService.CandidateSnapshot candidate =
                waitForCandidate(
                        session.getId(),
                        questionNumber,
                        targetBand
                );
        if (candidate == null) {
            return null;
        }

        Long poolQuestionId = candidate.poolQuestionId();
        if (poolQuestionId == null) {
            log.warn(
                    "Level Test prefetch candidate ignored because poolQuestionId is missing. candidateId={}, sessionId={}, questionNumber={}, band={}, status={}",
                    candidate.id(),
                    session.getId(),
                    questionNumber,
                    targetBand,
                    candidate.status()
            );
            candidateCommandService.expire(candidate.id());
            return null;
        }

        LevelTestQuestionPool poolQuestion = poolQueryService
                .findById(poolQuestionId)
                .orElse(null);
        if (poolQuestion != null && !healthyPoolQuestion(
                poolQuestion,
                "PREFETCH_CANDIDATE"
        )) {
            poolQuestion = null;
        }
        if (!compatiblePoolQuestion(
                poolQuestion,
                session,
                expected,
                targetBand,
                excludedHashes,
                preferredScenarioCategories
        )) {
            candidateCommandService.expire(candidate.id());
            return null;
        }

        LevelTestItem item = persistenceService.saveFromPool(
                session,
                questionNumber,
                poolQuestion
        );
        candidateCommandService.selectAndExpireOthers(
                candidate.id(),
                session.getId(),
                questionNumber
        );
        return new Resolution(item, "PREFETCH_CANDIDATE");
    }

    private LevelTestQuestionPool findReusablePoolQuestion(
            LevelTestSession session,
            LevelTestRecipe.Entry expected,
            int questionNumber,
            int targetBand,
            List<String> excludedHashes,
            List<String> preferredScenarioCategories
    ) {
        LevelTestQuestionPoolQueryService.PoolCounts counts =
                poolQueryService.counts(
                        session.getOriginLanguage(),
                        session.getLearningLanguage(),
                        expected.domain(),
                        expected.itemType(),
                        targetBand,
                        LevelTestSession.DEFAULT_GENERATION_POLICY_VERSION,
                        MODEL_CONFIG_VERSION
                );
        if (!poolPolicy.canReuse(
                counts.total(),
                counts.bucket(),
                expected.domain(),
                expected.itemType(),
                targetBand
        )) {
            return null;
        }
        for (LevelTestQuestionPool candidate : poolQueryService.findReusableCandidates(
                session.getOriginLanguage(),
                session.getLearningLanguage(),
                expected.domain(),
                expected.itemType(),
                targetBand,
                LevelTestSession.DEFAULT_GENERATION_POLICY_VERSION,
                MODEL_CONFIG_VERSION,
                excludedHashes
        )) {
            if (healthyPoolQuestion(candidate, "RUNTIME_REUSE")
                    && scenarioBalancePolicy.accepts(
                            candidate.getScenarioCategory(),
                            preferredScenarioCategories
                    )) {
                return candidate;
            }
        }
        return null;
    }

    private AiLevelTestQuestionResponseDto generateResponse(
            LevelTestSession session,
            LevelTestRecipe.Entry expected,
            int questionNumber,
            int targetBand,
            DiversityContext diversityContext,
            List<String> preferredScenarioCategories,
            boolean prefetch
    ) {
        String requestId = prefetch
                ? "level-prefetch-"
                        + session.getId()
                        + "-"
                        + questionNumber
                        + "-"
                        + targetBand
                : "level-question-"
                        + session.getId()
                        + "-"
                        + questionNumber;
        String idempotencyKey = prefetch
                ? "level:prefetch:"
                        + session.getId()
                        + ":"
                        + questionNumber
                        + ":"
                        + targetBand
                : "level:question:"
                        + session.getId()
                        + ":"
                        + questionNumber;
        if (audioUploadService.requiresReferenceAudio(
                expected.domain(),
                expected.itemType()
        ) && !audioUploadService.available()) {
            throw new BusinessException(
                    "Level Test Reference Audio 저장소가 준비되지 않았습니다.",
                    LanguageLearningErrorCode.AI_TTS_FAILED
            );
        }
        LevelTestReferenceAudioUploadDto referenceAudioUpload =
                audioUploadService.prepare(
                        session.getLearningLanguage(),
                        idempotencyKey,
                        expected.domain(),
                        expected.itemType()
                );
        AiLevelTestQuestionRequestDto request =
                new AiLevelTestQuestionRequestDto(
                        requestId,
                        idempotencyKey,
                        session.getId(),
                        questionNumber,
                        LevelTestRecipe.TOTAL_QUESTIONS,
                        expected.domain(),
                        expected.itemType(),
                        session.getOriginLanguage(),
                        session.getLearningLanguage(),
                        targetBand,
                        previousResults(session.getId()),
                        diversityContext,
                        preferredScenarioCategories,
                        LevelTestSession.DEFAULT_GENERATION_POLICY_VERSION,
                        MODEL_CONFIG_VERSION,
                        referenceAudioUpload
                );
        AiLevelTestQuestionResponseDto response =
                aiClient.generateLevelTestQuestion(request);
        validateResponse(
                response,
                session,
                expected,
                questionNumber,
                targetBand
        );
        validateScenarioBalance(response, preferredScenarioCategories);
        validateContentHealth(response, session.getLearningLanguage());
        validateAndVerifyReferenceAudio(
                response,
                referenceAudioUpload,
                expected.domain(),
                expected.itemType()
        );
        return response;
    }

    public LevelTestQuestionPool generateBatchPoolQuestion(
            String originLanguage,
            String learningLanguage,
            int questionNumber,
            int targetBand,
            String batchKey,
            DiversityContext diversityContext
    ) {
        LevelTestRecipe.Entry expected = recipe.entry(questionNumber);
        if (audioUploadService.requiresReferenceAudio(
                expected.domain(),
                expected.itemType()
        ) && !audioUploadService.available()) {
            return null;
        }

        String requestId = "level-pool-batch-" + batchKey;
        String idempotencyKey = "level:pool:batch:" + batchKey;
        LevelTestReferenceAudioUploadDto referenceAudioUpload =
                audioUploadService.prepare(
                        learningLanguage,
                        idempotencyKey,
                        expected.domain(),
                        expected.itemType()
                );
        List<String> preferredScenarioCategories = scenarioBalancePolicy.preferredForPool(
                poolQueryService.recentScenarioCategories(
                        originLanguage,
                        learningLanguage,
                        expected.domain(),
                        expected.itemType()
                ),
                questionNumber,
                expected.domain(),
                batchKey
        );
        AiLevelTestQuestionResponseDto response =
                aiClient.generateLevelTestPoolQuestion(
                        new AiLevelTestQuestionRequestDto(
                                requestId,
                                idempotencyKey,
                                0L,
                                questionNumber,
                                LevelTestRecipe.TOTAL_QUESTIONS,
                                expected.domain(),
                                expected.itemType(),
                                originLanguage,
                                learningLanguage,
                                targetBand,
                                List.of(),
                                diversityContext == null
                                        ? DiversityContext.empty()
                                        : diversityContext,
                                preferredScenarioCategories,
                                LevelTestSession.DEFAULT_GENERATION_POLICY_VERSION,
                                MODEL_CONFIG_VERSION,
                                referenceAudioUpload
                        )
                );
        validateBatchResponse(
                response,
                expected,
                originLanguage,
                learningLanguage,
                questionNumber,
                targetBand
        );
        validateScenarioBalance(response, preferredScenarioCategories);
        validateContentHealth(response, learningLanguage);
        validateAndVerifyReferenceAudio(
                response,
                referenceAudioUpload,
                expected.domain(),
                expected.itemType()
        );
        LevelTestQuestionPool registered = poolCommandService.register(
                originLanguage,
                learningLanguage,
                questionNumber,
                response,
                LevelTestSession.DEFAULT_GENERATION_POLICY_VERSION,
                MODEL_CONFIG_VERSION
        );
        cleanupUnusedReferenceAudio(response, registered);
        return registered;
    }

    private LevelTestQuestionCandidateQueryService.CandidateSnapshot waitForCandidate(
            Long sessionId,
            int questionNumber,
            int targetBand
    ) {
        LevelTestQuestionCandidateQueryService.CandidateSnapshot available =
                candidateQueryService.findAvailableSnapshot(
                        sessionId,
                        questionNumber,
                        targetBand
                ).orElse(null);
        if (available != null) {
            return available;
        }
        LevelTestQuestionCandidateQueryService.CandidateSnapshot current =
                candidateQueryService.findAnySnapshot(
                        sessionId,
                        questionNumber,
                        targetBand
                ).orElse(null);
        if (current == null
                || current.status()
                != LevelTestQuestionCandidateStatus.GENERATING
                || candidateWaitMs <= 0) {
            return null;
        }

        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(candidateWaitMs);
        while (System.nanoTime() < deadline) {
            try {
                Thread.sleep(Math.min(200L, Math.max(25L, candidateWaitMs)));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return null;
            }
            available = candidateQueryService.findAvailableSnapshot(
                    sessionId,
                    questionNumber,
                    targetBand
            ).orElse(null);
            if (available != null) {
                return available;
            }
            current = candidateQueryService.findAnySnapshot(
                    sessionId,
                    questionNumber,
                    targetBand
            ).orElse(null);
            if (current == null
                    || current.status()
                    != LevelTestQuestionCandidateStatus.GENERATING) {
                return null;
            }
        }
        return null;
    }

    private void validateAndVerifyReferenceAudio(
            AiLevelTestQuestionResponseDto response,
            LevelTestReferenceAudioUploadDto upload,
            LevelTestDomain domain,
            LevelTestItemType itemType
    ) {
        boolean required = audioUploadService.requiresReferenceAudio(
                domain,
                itemType
        );
        if (required && upload == null) {
            throw new BusinessException(
                    "Level Test Reference Audio 업로드 경로가 준비되지 않았습니다.",
                    LanguageLearningErrorCode.AI_TTS_FAILED
            );
        }
        if (upload == null) {
            return;
        }
        if (!required
                || response.referenceAudio() == null
                || !Objects.equals(
                        upload.objectKey(),
                        response.referenceAudio().objectKey()
                )
                || !Objects.equals(
                        upload.contentType(),
                        response.referenceAudio().contentType()
                )) {
            throw invalidContract();
        }
        audioUploadService.verify(response.referenceAudio());
    }

    private void cleanupUnusedReferenceAudio(
            AiLevelTestQuestionResponseDto response,
            LevelTestQuestionPool registered
    ) {
        if (response.referenceAudio() == null
                || registered == null
                || Objects.equals(
                        response.referenceAudio().objectKey(),
                        registered.getReferenceAudioObjectKey()
                )) {
            return;
        }
        audioUploadService.cleanupQuietly(response.referenceAudio());
    }

    private void validateBatchResponse(
            AiLevelTestQuestionResponseDto response,
            LevelTestRecipe.Entry expected,
            String originLanguage,
            String learningLanguage,
            int questionNumber,
            int targetBand
    ) {
        if (response == null
                || response.sessionId() == null
                || response.sessionId() != 0L
                || response.questionNumber() != questionNumber
                || response.totalQuestions() != LevelTestRecipe.TOTAL_QUESTIONS
                || response.domain() != expected.domain()
                || response.itemType() != expected.itemType()
                || response.complexityBand() != targetBand
                || !Objects.equals(response.instructionLanguage(), learningLanguage)
                || blank(response.instruction())
                || blank(response.promptText())
                || response.answerMode() == null
                || response.internalAnswerKey() == null
                || response.referencePayload() == null
                || response.diversityMetadata() == null
                || Boolean.TRUE.equals(
                        response.diversityMetadata().requiresBackgroundKnowledge()
                )
                || blank(response.diversityMetadata().contentHash())
                || blank(response.diversityMetadata().similarityKey())
                || blank(response.generationVersion())
                || !promptVersionAtLeast(response.promptVersion(), 9)) {
            throw invalidContract();
        }
        validateBatchAnswerContract(
                response,
                originLanguage,
                learningLanguage
        );
        validateReferenceContract(response);
    }

    private void validateBatchAnswerContract(
            AiLevelTestQuestionResponseDto response,
            String originLanguage,
            String learningLanguage
    ) {
        List<LevelTestOptionDto> options = response.options() == null
                ? List.of()
                : response.options();
        if (response.answerMode() == LevelTestAnswerMode.CHOICE) {
            validateChoiceContract(response, options);
            return;
        }
        if (!options.isEmpty() || blank(response.answerLanguage())) {
            throw invalidContract();
        }
        String expectedAnswerLanguage = switch (response.itemType()) {
            case LISTENING_INTERPRETATION -> originLanguage;
            case LISTENING_DICTATION,
                 WRITING_TRANSLATION,
                 WRITING_GUIDED_SENTENCE,
                 WRITING_SCENARIO_RESPONSE,
                 WRITING_SHORT_PARAGRAPH,
                 SPEAKING_REPEAT,
                 SPEAKING_GUIDED_RESPONSE,
                 SPEAKING_SHORT_RESPONSE -> learningLanguage;
            default -> null;
        };
        if (expectedAnswerLanguage != null
                && !Objects.equals(
                        expectedAnswerLanguage,
                        response.answerLanguage()
                )) {
            throw invalidContract();
        }
    }

    private boolean compatiblePoolQuestion(
            LevelTestQuestionPool poolQuestion,
            LevelTestSession session,
            LevelTestRecipe.Entry expected,
            int targetBand,
            List<String> excludedHashes,
            List<String> preferredScenarioCategories
    ) {
        return poolQuestion != null
                && poolQuestion.isActive()
                && contentPolicy.inspect(poolQuestion).valid()
                && Objects.equals(
                        poolQuestion.getOriginLanguage(),
                        session.getOriginLanguage()
                )
                && Objects.equals(
                        poolQuestion.getLearningLanguage(),
                        session.getLearningLanguage()
                )
                && poolQuestion.getDomain() == expected.domain()
                && poolQuestion.getItemType() == expected.itemType()
                && poolQuestion.getComplexityBand() == targetBand
                && Objects.equals(
                        poolQuestion.getPolicyVersion(),
                        LevelTestSession.DEFAULT_GENERATION_POLICY_VERSION
                )
                && Objects.equals(
                        poolQuestion.getModelConfigVersion(),
                        MODEL_CONFIG_VERSION
                )
                && scenarioBalancePolicy.accepts(
                        poolQuestion.getScenarioCategory(),
                        preferredScenarioCategories
                )
                && (excludedHashes == null
                        || !excludedHashes.contains(
                                poolQuestion.getContentHash()
                        ));
    }

    private boolean healthyPoolQuestion(
            LevelTestQuestionPool poolQuestion,
            String source
    ) {
        LevelTestQuestionContentPolicy.Health health = contentPolicy.inspect(poolQuestion);
        if (health.valid()) {
            return true;
        }
        quarantinePoolQuestion(poolQuestion.getId(), health.reason(), source);
        return false;
    }

    private void quarantinePoolQuestion(
            Long poolQuestionId,
            String reason,
            String source
    ) {
        poolCommandService.quarantine(poolQuestionId, reason);
        log.warn(
                "Invalid Level Test pool question quarantined. poolQuestionId={}, source={}, reason={}",
                poolQuestionId,
                source,
                reason
        );
    }

    private void validateScenarioBalance(
            AiLevelTestQuestionResponseDto response,
            List<String> preferredScenarioCategories
    ) {
        if (response == null
                || response.diversityMetadata() == null
                || !scenarioBalancePolicy.accepts(
                        response.diversityMetadata().scenarioCategory(),
                        preferredScenarioCategories
                )) {
            log.warn(
                    "Generated Level Test question rejected by scenario balance. domain={}, itemType={}, band={}, scenarioCategory={}, preferred={}",
                    response == null ? null : response.domain(),
                    response == null ? null : response.itemType(),
                    response == null ? null : response.complexityBand(),
                    response == null || response.diversityMetadata() == null
                            ? null
                            : response.diversityMetadata().scenarioCategory(),
                    preferredScenarioCategories
            );
            throw invalidContract();
        }
    }

    private void validateContentHealth(
            AiLevelTestQuestionResponseDto response,
            String learningLanguage
    ) {
        LevelTestQuestionContentPolicy.Health health = contentPolicy.inspect(
                response,
                learningLanguage
        );
        if (!health.valid()) {
            log.warn(
                    "Generated Level Test question rejected by content health policy. domain={}, itemType={}, band={}, reason={}",
                    response == null ? null : response.domain(),
                    response == null ? null : response.itemType(),
                    response == null ? null : response.complexityBand(),
                    health.reason()
            );
            throw invalidContract();
        }
    }

    private List<LevelTestPreviousResultDto> previousResults(Long sessionId) {
        List<LevelTestPreviousResultDto> result = new ArrayList<>();

        for (LevelTestItem item : itemRepository
                .findAllBySessionIdOrderByQuestionNumberAsc(sessionId)) {
            LevelTestResponse response = responseRepository
                    .findByItemId(item.getId())
                    .orElse(null);
            if (response == null) {
                continue;
            }
            LevelTestEvaluation evaluation = evaluationRepository
                    .findByResponseId(response.getId())
                    .orElse(null);
            if (evaluation == null) {
                continue;
            }
            result.add(new LevelTestPreviousResultDto(
                    item.getQuestionNumber(),
                    item.getDomain(),
                    item.getItemType(),
                    evaluation.getScore(),
                    item.getComplexityBandValue(),
                    evaluation.isEvaluable()
            ));
        }
        return result;
    }

    private void validateResponse(
            AiLevelTestQuestionResponseDto response,
            LevelTestSession session,
            LevelTestRecipe.Entry expected,
            int questionNumber,
            int targetBand
    ) {
        if (response == null
                || response.sessionId() == null
                || !response.sessionId().equals(session.getId())
                || response.questionNumber() != questionNumber
                || response.totalQuestions() != LevelTestRecipe.TOTAL_QUESTIONS
                || response.domain() != expected.domain()
                || response.itemType() != expected.itemType()
                || response.complexityBand() != targetBand
                || !Objects.equals(
                        response.instructionLanguage(),
                        session.getLearningLanguage()
                )
                || blank(response.instruction())
                || blank(response.promptText())
                || response.answerMode() == null
                || response.internalAnswerKey() == null
                || response.referencePayload() == null
                || response.diversityMetadata() == null
                || Boolean.TRUE.equals(
                        response.diversityMetadata()
                                .requiresBackgroundKnowledge()
                )
                || blank(response.diversityMetadata().contentHash())
                || blank(response.diversityMetadata().similarityKey())
                || blank(response.generationVersion())
                || !promptVersionAtLeast(response.promptVersion(), 9)) {
            throw invalidContract();
        }

        validateAnswerContract(response, session);
        validateReferenceContract(response);
    }

    private void validateAnswerContract(
            AiLevelTestQuestionResponseDto response,
            LevelTestSession session
    ) {
        List<LevelTestOptionDto> options = response.options() == null
                ? List.of()
                : response.options();

        if (response.answerMode() == LevelTestAnswerMode.CHOICE) {
            validateChoiceContract(response, options);
        } else if (!options.isEmpty() || blank(response.answerLanguage())) {
            throw invalidContract();
        }

        String expectedAnswerLanguage = switch (response.itemType()) {
            case LISTENING_INTERPRETATION -> session.getOriginLanguage();
            case LISTENING_DICTATION,
                 WRITING_TRANSLATION,
                 WRITING_GUIDED_SENTENCE,
                 WRITING_SCENARIO_RESPONSE,
                 WRITING_SHORT_PARAGRAPH,
                 SPEAKING_REPEAT,
                 SPEAKING_GUIDED_RESPONSE,
                 SPEAKING_SHORT_RESPONSE -> session.getLearningLanguage();
            default -> null;
        };

        if (expectedAnswerLanguage != null
                && !Objects.equals(
                        expectedAnswerLanguage,
                        response.answerLanguage()
                )) {
            throw invalidContract();
        }
    }

    private void validateChoiceContract(
            AiLevelTestQuestionResponseDto response,
            List<LevelTestOptionDto> options
    ) {
        if (response.itemType() == LevelTestItemType.GRAMMAR_SENTENCE_ORDER) {
            if (options.size() < 2) {
                throw invalidContract();
            }
            List<String> keys = options.stream()
                    .map(LevelTestOptionDto::key)
                    .toList();
            List<String> correctOrder =
                    response.internalAnswerKey().correctOrder();
            if (new HashSet<>(keys).size() != keys.size()
                    || correctOrder == null
                    || !new HashSet<>(keys).equals(
                            new HashSet<>(correctOrder)
                    )
                    || correctOrder.size() != keys.size()) {
                throw invalidContract();
            }
            return;
        }

        if (options.size() != 4) {
            throw invalidContract();
        }
        Set<String> keys = new HashSet<>(
                options.stream().map(LevelTestOptionDto::key).toList()
        );
        if (keys.size() != 4
                || !keys.contains(
                        response.internalAnswerKey().correctOptionKey()
                )) {
            throw invalidContract();
        }
    }

    private void validateReferenceContract(
            AiLevelTestQuestionResponseDto response
    ) {
        Map<String, Object> payload = response.referencePayload();
        if (response.domain() == LevelTestDomain.LISTENING) {
            String sourceText = asString(payload.get("sourceText"));
            String listeningQuestion = asString(payload.get("listeningQuestion"));
            if (blank(sourceText)
                    || blank(listeningQuestion)
                    || !response.promptText().trim().equals(listeningQuestion.trim())
                    || !promptVersionAtLeast(response.promptVersion(), 7)) {
                throw invalidContract();
            }
            if (response.itemType()
                    == LevelTestItemType.LISTENING_INTERPRETATION) {
                int meanings = listSize(payload.get("referenceMeanings"));
                int units = listSize(payload.get("keyMeaningUnits"));
                if (meanings < 2
                        || meanings > 3
                        || units < 2
                        || units > 5) {
                    throw invalidContract();
                }
            }
        }

        if (response.itemType() == LevelTestItemType.SPEAKING_REPEAT
                && blank(asString(payload.get("referenceText")))) {
            throw invalidContract();
        }
    }

    private boolean promptVersionAtLeast(String promptVersion, int minimum) {
        String prefix = "level-test-multiskill-prompt-v";
        if (blank(promptVersion) || !promptVersion.startsWith(prefix)) {
            return false;
        }
        try {
            return Integer.parseInt(promptVersion.substring(prefix.length())) >= minimum;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private int listSize(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException invalidContract() {
        return new BusinessException(
                "AI Level Test 문제 생성 계약이 유효하지 않습니다.",
                LanguageLearningErrorCode.AI_SCHEMA_INVALID
        );
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private record Resolution(LevelTestItem item, String source) {
    }
}
