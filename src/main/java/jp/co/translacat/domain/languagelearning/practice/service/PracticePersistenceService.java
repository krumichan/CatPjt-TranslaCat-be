package jp.co.translacat.domain.languagelearning.practice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PersonalizedVocabularyPlanDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeGeneratedQuestionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeOptionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeReviewTargetDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiPracticeGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiPracticeGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeQuestion;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeSet;
import jp.co.translacat.domain.languagelearning.practice.entity.VocabularyMastery;
import jp.co.translacat.domain.languagelearning.practice.enums.PracticeGenerationStatus;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeQuestionRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeSetRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.VocabularyMasteryRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class PracticePersistenceService {
    static final String STORED_REQUEST_INVALID = "STORED_REQUEST_INVALID";
    static final String LEASE_RECOVERY_EXHAUSTED = "LEASE_RECOVERY_EXHAUSTED";
    private final PracticeSetRepository setRepository;
    private final PracticeQuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final VocabularyMasteryRepository masteryRepository;
    private final LanguageLearningJsonCodec jsonCodec;

    @Transactional
    public PracticeSet createPending(Long userId, AiPracticeGenerationRequestDto request) {
        // Serialize first creation, including the absent-row case, across server instances.
        User user = userRepository.findLockedById(userId).orElseThrow(this::notFound);
        var existing = setRepository.findByUserIdAndLearningDateAndDomainAndMode(
                userId, request.generationDate(), request.domain(), request.mode()
        );
        if (existing.isPresent()) return existing.get();
        PracticeSet set = PracticeSet.create(
                user, request.generationDate(), request.domain(), request.mode(),
                request.originLanguage(), request.learningLanguage(), request.questionCount(),
                request.complexityBand()
        );
        set.queueGeneration(jsonCodec.write(request));
        return setRepository.save(set);
    }

    @Transactional
    public PracticeSet retry(Long userId, Long setId) {
        PracticeSet set = setRepository.findLockedById(setId).orElseThrow(this::notFound);
        if (!Objects.equals(set.getUser().getId(), userId)) throw notFound();
        if (set.getGenerationStatus() == PracticeGenerationStatus.PARTIAL
                || set.getGenerationStatus() == PracticeGenerationStatus.FAILED) {
            // Neither questions nor attempts are deleted. The worker locates the first gap.
            set.resumeGeneration();
        }
        return set;
    }

    @Transactional(readOnly = true)
    public List<Long> pendingIds(LocalDateTime now, LocalDateTime staleBefore) {
        return Stream.concat(
                setRepository.findDueByGenerationStatus(
                        PracticeGenerationStatus.PENDING, now, 20
                ).stream(),
                setRepository.findTop20ByGenerationStatusAndGenerationStartedAtBeforeOrderByGenerationStartedAtAsc(
                        PracticeGenerationStatus.GENERATING, staleBefore
                ).stream()
        ).map(PracticeSet::getId).distinct().toList();
    }

    @Transactional
    public Optional<GenerationClaim> claim(
            Long setId,
            LocalDateTime now,
            LocalDateTime staleBefore,
            int automaticRetryLimit
    ) {
        PracticeSet set = setRepository.findLockedById(setId).orElse(null);
        if (set == null) return Optional.empty();
        boolean stale = set.getGenerationStatus() == PracticeGenerationStatus.GENERATING
                && set.getGenerationStartedAt() != null
                && set.getGenerationStartedAt().isBefore(staleBefore);
        if ((!set.isGenerationDue(now)) && !stale) {
            return Optional.empty();
        }
        List<PracticeQuestion> questions = questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(setId);
        if (stale) {
            if (set.getGenerationRetryCount() >= Math.max(0, automaticRetryLimit)) {
                set.failGeneration(LEASE_RECOVERY_EXHAUSTED, !questions.isEmpty());
                return Optional.empty();
            }
            set.registerGenerationRecovery();
        }
        int order = firstMissingOrder(questions, set.getQuestionCount());
        if (order > set.getQuestionCount()) {
            set.finishGeneration();
            return Optional.empty();
        }
        if (set.getGenerationRequestJson() == null) {
            set.failGeneration(STORED_REQUEST_INVALID, !questions.isEmpty());
            return Optional.empty();
        }
        AiPracticeGenerationRequestDto original;
        try {
            original = jsonCodec.read(set.getGenerationRequestJson(), AiPracticeGenerationRequestDto.class);
        } catch (RuntimeException error) {
            set.failGeneration(STORED_REQUEST_INVALID, !questions.isEmpty());
            return Optional.empty();
        }
        if (original == null) {
            set.failGeneration(STORED_REQUEST_INVALID, !questions.isEmpty());
            return Optional.empty();
        }
        if (isContextualChoice(original) && original.vocabularyPlan() != null) {
            try {
                PracticeGenerationWorker.validateContextualChoiceAcceptedPrefix(
                        original.vocabularyPlan(),
                        questions.stream().filter(question -> question.getOrderNo() < order)
                                .map(this::generatedQuestion).toList()
                );
            } catch (RuntimeException error) {
                set.failGeneration(LanguageLearningErrorCode.AI_SCHEMA_INVALID, !questions.isEmpty());
                return Optional.empty();
            }
        }
        List<PracticeGeneratedQuestionDto> previous = questions.stream()
                .filter(question -> question.getOrderNo() < order)
                .map(this::generatedQuestion).toList();
        String token = UUID.randomUUID().toString();
        AiPracticeGenerationRequestDto request;
        try {
            request = itemRequest(original, order, previous, token);
        } catch (RuntimeException error) {
            set.failGeneration(STORED_REQUEST_INVALID, !questions.isEmpty());
            return Optional.empty();
        }
        set.claimGeneration(token, now);
        return Optional.of(new GenerationClaim(
                setId, order, token, set.getGenerationRetryCount(), request
        ));
    }

    @Transactional
    public boolean persistVocabularyPlan(
            GenerationClaim claim,
            PersonalizedVocabularyPlanDto vocabularyPlan
    ) {
        PracticeSet set = setRepository.findLockedById(claim.setId()).orElseThrow(this::notFound);
        if (!set.ownsGeneration(claim.token())) return false;
        if (!isContextualChoice(claim.request())) return true;
        if (vocabularyPlan == null) throw invalidAiResponse();

        AiPracticeGenerationRequestDto storedRequest = storedRequest(set);
        if (!isContextualChoice(storedRequest)) throw invalidAiResponse();
        if (storedRequest.vocabularyPlan() != null) {
            if (!storedRequest.vocabularyPlan().equals(vocabularyPlan)) throw invalidAiResponse();
            return true;
        }
        set.updateGenerationRequest(jsonCodec.write(withVocabularyPlan(storedRequest, vocabularyPlan)));
        return true;
    }

    @Transactional
    public boolean append(GenerationClaim claim, AiPracticeGenerationResponseDto generated) {
        PracticeSet set = setRepository.findLockedById(claim.setId()).orElseThrow(this::notFound);
        if (!set.ownsGeneration(claim.token())) return false;
        List<PracticeQuestion> existing = questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(set.getId());
        if (firstMissingOrder(existing, set.getQuestionCount()) != claim.order()) return false;
        if (isContextualChoice(claim.request())) {
            PersonalizedVocabularyPlanDto persistedPlan = storedRequest(set).vocabularyPlan();
            if (persistedPlan == null || generated.vocabularyPlan() == null
                    || (claim.request().vocabularyPlan() != null
                        && !claim.request().vocabularyPlan().equals(persistedPlan))) {
                throw invalidAiResponse();
            }
            PracticeGenerationWorker.validateContextualChoiceAcceptedPrefix(
                    persistedPlan,
                    existing.stream().filter(question -> question.getOrderNo() < claim.order())
                            .map(this::generatedQuestion).toList()
            );
            PracticeGenerationWorker.validateContextualChoicePlanDelta(
                    withVocabularyPlan(claim.request(), persistedPlan), generated.vocabularyPlan()
            );
            if (!persistedPlan.equals(generated.vocabularyPlan())) {
                set.updateGenerationRequest(jsonCodec.write(withVocabularyPlan(
                        storedRequest(set), generated.vocabularyPlan()
                )));
            }
        }
        PracticeGeneratedQuestionDto item = withOrder(generated.questions().getFirst(), claim.order());
        if (set.getDomain() == PracticeDomain.VOCABULARY && existing.stream().anyMatch(question ->
                sameExpression(question.getCanonicalKey(), item.canonicalKey())
                        || sameExpression(question.getTargetExpression(), item.targetExpression()))) {
            throw new BusinessException("Vocabulary AI 응답에 이미 생성된 표현이 중복되었습니다.",
                    LanguageLearningErrorCode.AI_SCHEMA_INVALID);
        }
        PracticeQuestion savedQuestion = PracticeQuestion.create(
                set, item, jsonCodec.write(item.options()), jsonCodec.write(item.correctAnswer()),
                jsonCodec.write(item.vocabularyCandidates() == null ? List.of() : item.vocabularyCandidates())
        );
        questionRepository.save(savedQuestion);
        if (isContextualChoice(claim.request())) {
            log.info("Practice generation stage. setId={} order={} requestId={} stage=QUESTION_APPENDED",
                    claim.setId(), claim.order(), claim.request().requestId());
        }
        set.markGenerated(generated.promptVersion());
        if (set.getDomain() == PracticeDomain.VOCABULARY
                && item.canonicalKey() != null && !item.canonicalKey().isBlank()) {
            User user = set.getUser();
            VocabularyMastery mastery = masteryRepository
                    .findByUserIdAndCanonicalKey(user.getId(), item.canonicalKey())
                    .orElseGet(() -> masteryRepository.save(
                            VocabularyMastery.create(user, item.canonicalKey(), item.targetExpression())
                    ));
            mastery.markSelected(set.getLearningDate());
        }
        var allQuestions = new java.util.ArrayList<>(existing);
        allQuestions.add(savedQuestion);
        if (firstMissingOrder(allQuestions, set.getQuestionCount()) > set.getQuestionCount()) {
            set.finishGeneration();
            return false;
        }
        // Committing this transaction publishes exactly one question before any next AI call.
        set.resumeGeneration();
        return true;
    }

    @Transactional
    public void fail(GenerationClaim claim, String message) {
        setRepository.findLockedById(claim.setId())
                .filter(set -> set.ownsGeneration(claim.token()))
                .ifPresent(set -> set.failGeneration(
                        message, questionRepository.countByPracticeSetId(set.getId()) > 0
                ));
    }

    @Transactional
    public void recordInfrastructureFailure(
            GenerationClaim claim,
            String failureCode,
            LocalDateTime retryAt,
            int automaticRetryLimit
    ) {
        setRepository.findLockedById(claim.setId())
                .filter(set -> set.ownsGeneration(claim.token()))
                .ifPresent(set -> {
                    boolean hasQuestions = questionRepository.countByPracticeSetId(set.getId()) > 0;
                    if (set.getGenerationRetryCount() < automaticRetryLimit) {
                        set.deferGeneration(retryAt);
                    } else {
                        set.failGeneration(failureCode, hasQuestions);
                    }
                });
    }

    static int firstMissingOrder(List<PracticeQuestion> questions, int target) {
        var orders = questions.stream().map(PracticeQuestion::getOrderNo)
                .collect(java.util.stream.Collectors.toSet());
        for (int order = 1; order <= target; order++) {
            if (!orders.contains(order)) return order;
        }
        return target + 1;
    }

    static AiPracticeGenerationRequestDto itemRequest(
            AiPracticeGenerationRequestDto original, int order,
            List<PracticeGeneratedQuestionDto> previous, String token
    ) {
        int difficulty = difficultyAt(original, order);
        int easier = difficulty == 0 ? 1 : 0;
        int current = difficulty == 1 ? 1 : 0;
        int reviewIndex = order - 1;
        List<PracticeReviewTargetDto> reviews = original.reviewTargets() == null ? List.of() : original.reviewTargets();
        boolean review = reviewIndex < original.reviewQuestionCount() && reviewIndex < reviews.size();
        var orderedReviews = new java.util.ArrayList<>(reviews);
        if (review) {
            PracticeReviewTargetDto selected = orderedReviews.remove(reviewIndex);
            orderedReviews.addFirst(selected);
        }
        return new AiPracticeGenerationRequestDto(
                "practice-item-" + order + "-" + token,
                original.domain(), original.mode(), original.originLanguage(), original.learningLanguage(),
                1, original.complexityBand(), easier, current, 1 - easier - current,
                original.selectedKeywords(), original.weakSignals(), original.recentMistakes(),
                List.copyOf(orderedReviews), review ? 1 : 0,
                original.generationDate(), previous, original.vocabularyPlan()
        );
    }

    private AiPracticeGenerationRequestDto storedRequest(PracticeSet set) {
        try {
            return jsonCodec.read(set.getGenerationRequestJson(), AiPracticeGenerationRequestDto.class);
        } catch (RuntimeException error) {
            throw new BusinessException(
                    "Stored Reading/Vocabulary generation request is invalid.",
                    LanguageLearningErrorCode.AI_SCHEMA_INVALID
            );
        }
    }

    private static AiPracticeGenerationRequestDto withVocabularyPlan(
            AiPracticeGenerationRequestDto request,
            PersonalizedVocabularyPlanDto vocabularyPlan
    ) {
        return new AiPracticeGenerationRequestDto(
                request.requestId(), request.domain(), request.mode(), request.originLanguage(),
                request.learningLanguage(), request.questionCount(), request.complexityBand(),
                request.easierCount(), request.currentCount(), request.challengeCount(),
                request.selectedKeywords(), request.weakSignals(), request.recentMistakes(),
                request.reviewTargets(), request.reviewQuestionCount(), request.generationDate(),
                request.previousQuestions(), vocabularyPlan
        );
    }

    private static boolean isContextualChoice(AiPracticeGenerationRequestDto request) {
        return request.domain() == PracticeDomain.VOCABULARY
                && "CONTEXTUAL_CHOICE".equals(request.mode());
    }

    private static BusinessException invalidAiResponse() {
        return new BusinessException(
                "Reading/Vocabulary AI response contract is invalid.",
                LanguageLearningErrorCode.AI_SCHEMA_INVALID
        );
    }

    private static int difficultyAt(AiPracticeGenerationRequestDto original, int order) {
        int[] remaining = {original.easierCount(), original.currentCount(), original.challengeCount()};
        int position = 0;
        // Match the AI batch curriculum: CURRENT, EASIER, CURRENT, CHALLENGE, consuming quotas.
        while (position < original.questionCount()) {
            boolean progressed = false;
            for (int difficulty : new int[]{1, 0, 1, 2}) {
                if (remaining[difficulty] <= 0) continue;
                remaining[difficulty]--;
                progressed = true;
                if (++position == order) return difficulty;
            }
            if (!progressed) break;
        }
        throw new IllegalArgumentException("Generation difficulty distribution does not cover the requested slot.");
    }

    private static boolean sameExpression(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) return false;
        return java.text.Normalizer.normalize(left, java.text.Normalizer.Form.NFKC).strip()
                .equalsIgnoreCase(java.text.Normalizer.normalize(right, java.text.Normalizer.Form.NFKC).strip());
    }

    private PracticeGeneratedQuestionDto generatedQuestion(PracticeQuestion question) {
        return new PracticeGeneratedQuestionDto(
                question.getOrderNo(), question.getQuestionType(), question.getDifficulty(),
                question.getComplexityBand(), question.getPassageId(), question.getPassageText(), question.getPrompt(),
                jsonCodec.read(question.getOptionsJson(), new TypeReference<List<PracticeOptionDto>>() {}),
                jsonCodec.read(question.getCorrectAnswerJson(), new TypeReference<List<String>>() {}),
                question.getSkillTag(), question.getEvidenceText(), question.getExplanationOrigin(),
                question.getExplanationLearning(), question.getTargetExpression(), question.getCanonicalKey(),
                question.isReviewTarget(),
                jsonCodec.read(question.getVocabularyCandidatesJson(), new TypeReference<List<String>>() {})
        );
    }

    private PracticeGeneratedQuestionDto withOrder(PracticeGeneratedQuestionDto item, int order) {
        return new PracticeGeneratedQuestionDto(
                order, item.questionType(), item.difficulty(), item.complexityBand(), item.passageId(),
                item.passageText(), item.prompt(), item.options(), item.correctAnswer(), item.skillTag(),
                item.evidenceText(), item.explanationOrigin(), item.explanationLearning(), item.targetExpression(),
                item.canonicalKey(), item.reviewTarget(), item.vocabularyCandidates()
        );
    }

    private BusinessException notFound() {
        return new BusinessException("Reading/Vocabulary 학습 세트를 찾을 수 없습니다.",
                LanguageLearningErrorCode.DAILY_SET_NOT_FOUND);
    }

    public record GenerationClaim(
            Long setId,
            int order,
            String token,
            int infrastructureRetryCount,
            AiPracticeGenerationRequestDto request
    ) {}
}
