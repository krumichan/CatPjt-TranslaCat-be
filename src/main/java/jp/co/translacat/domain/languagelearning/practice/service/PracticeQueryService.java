package jp.co.translacat.domain.languagelearning.practice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeOptionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiPracticeGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.practice.dto.response.*;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeAttempt;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeQuestion;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeSet;
import jp.co.translacat.domain.languagelearning.practice.enums.PracticeGenerationStatus;
import jp.co.translacat.domain.languagelearning.practice.policy.PracticeAvailabilityPolicy;
import jp.co.translacat.domain.languagelearning.practice.policy.ReadingPassageExpressionPolicy;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeAttemptRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeMetricScoreRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeQuestionRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeSetRepository;
import jp.co.translacat.domain.languagelearning.setting.port.UserSettingsGateway;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PracticeQueryService {
    private final PracticeSetRepository setRepository;
    private final PracticeQuestionRepository questionRepository;
    private final PracticeAttemptRepository attemptRepository;
    private final PracticeMetricScoreRepository metricRepository;
    private final LanguageLearningJsonCodec jsonCodec;
    private final UserSettingsGateway settingQueryService;

    public PracticeSet getOwned(Long userId, Long setId) {
        return setRepository.findByIdAndUserId(setId, userId)
                .orElseThrow(this::notFound);
    }

    public PracticeSetResponseDto get(Long userId, Long setId) {
        return toResponse(getOwned(userId, setId));
    }

    public List<PracticeTodayModeStatusResponseDto> getTodayStatus(
            Long userId,
            PracticeDomain domain
    ) {
        var today = settingQueryService.resolveToday(userId);
        return setRepository.findAllByUserIdAndLearningDateAndDomainOrderByIdAsc(
                        userId, today, domain
                ).stream()
                .filter(set -> domain != PracticeDomain.VOCABULARY
                        || "CONTEXTUAL_CHOICE".equals(set.getMode()))
                .map(set -> new PracticeTodayModeStatusResponseDto(
                        set.getMode(),
                        set.getId(),
                        set.getStatus(),
                        Math.toIntExact(attemptRepository.countByQuestionPracticeSetIdAndAttemptNo(
                                set.getId(), 1
                        )),
                        set.getQuestionCount(),
                        set.getOfficialScore(),
                        set.getGenerationStatus(),
                        Math.toIntExact(questionRepository.countByPracticeSetId(set.getId())),
                        generationFailureReason(set,
                                questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(set.getId()))
                ))
                .toList();
    }

    public PracticeSetResponseDto toResponse(PracticeSet set) {
        List<PracticeQuestion> storedQuestions = questionRepository
                .findAllByPracticeSetIdOrderByOrderNoAsc(set.getId());
        Map<Long, List<PracticeAttempt>> attemptsByQuestion = storedQuestions.stream().collect(Collectors.toMap(
                PracticeQuestion::getId,
                question -> attemptRepository.findAllByQuestionIdOrderByAttemptNoAsc(question.getId())
        ));
        Set<String> completedPassages = set.getDomain() == PracticeDomain.READING
                ? ReadingPassageExpressionPolicy.completedPassages(
                storedQuestions, question -> !attemptsByQuestion.get(question.getId()).isEmpty())
                : Set.of();
        List<PracticeQuestionResponseDto> questions = storedQuestions.stream()
                .map(question -> questionResponse(question, attemptsByQuestion.get(question.getId()),
                        question.getPassageId() != null && completedPassages.contains(question.getPassageId())))
                .toList();
        int answered = (int) questions.stream().filter(PracticeQuestionResponseDto::answered).count();
        int correct = (int) questions.stream()
                .filter(PracticeQuestionResponseDto::answered)
                .filter(q -> q.attempts().stream().anyMatch(a -> a.official() && a.correct()))
                .count();
        List<PracticeMetricResponseDto> metrics = metricRepository
                .findAllByPracticeSetIdOrderBySkillTagAsc(set.getId())
                .stream()
                .map(value -> new PracticeMetricResponseDto(
                        value.getSkillTag(), value.getScore(), value.getSampleCount()
                ))
                .toList();
        return new PracticeSetResponseDto(
                set.getId(),
                set.getLearningDate(),
                set.getDomain(),
                set.getMode(),
                set.getStatus(),
                set.getQuestionCount(),
                answered,
                correct,
                set.getOfficialScore(),
                set.getComplexityBand(),
                set.getPromptVersion(),
                metrics,
                questions,
                set.getGenerationStatus(),
                questions.size(),
                generationFailureReason(set, storedQuestions)
        );
    }

    private String generationFailureReason(PracticeSet set, List<PracticeQuestion> questions) {
        if (set.getDomain() != PracticeDomain.READING || !"STRUCTURE".equals(set.getMode())
                || (set.getGenerationStatus() != PracticeGenerationStatus.PARTIAL
                && set.getGenerationStatus() != PracticeGenerationStatus.FAILED)
                || set.getGenerationRequestJson() == null) return set.getGenerationFailureMessage();
        try {
            var original = jsonCodec.read(set.getGenerationRequestJson(), AiPracticeGenerationRequestDto.class);
            int missing = PracticePersistenceService.firstMissingOrder(questions, set.getQuestionCount());
            if (PracticeAvailabilityPolicy.needsAnyNewB5Structure(
                    original, PracticePersistenceService.readingSlotTargets(original), missing)) {
                return PracticeAvailabilityPolicy.B5_STRUCTURE_DEFERRED;
            }
        } catch (RuntimeException ignored) {
            // Preserve the stored request error; a read must never rewrite historic rows.
        }
        return set.getGenerationFailureMessage();
    }

    private PracticeQuestionResponseDto questionResponse(
            PracticeQuestion question, List<PracticeAttempt> attempts, boolean passageCompleted
    ) {
        boolean answered = !attempts.isEmpty();
        PracticeAttempt latest = answered ? attempts.get(attempts.size() - 1) : null;
        List<String> correctAnswer = answered
                ? jsonCodec.read(question.getCorrectAnswerJson(), new TypeReference<List<String>>() {
        })
                : List.of();
        return new PracticeQuestionResponseDto(
                question.getId(),
                question.getOrderNo(),
                question.getQuestionType(),
                question.getDifficulty(),
                question.getComplexityBand(),
                question.getPassageId(),
                question.getPassageText(),
                question.getPrompt(),
                jsonCodec.read(question.getOptionsJson(), new TypeReference<List<PracticeOptionDto>>() {
                        })
                        .stream()
                        .map(value -> new PracticeOptionResponseDto(value.key(), value.text()))
                        .toList(),
                question.getSkillTag(),
                question.getTargetExpression(),
                question.isReviewTarget(),
                passageCompleted ? ReadingPassageExpressionPolicy.readCandidates(
                        jsonCodec, question.getVocabularyCandidatesJson(), question.getPassageText()
                ) : List.of(),
                answered,
                latest != null && latest.isCorrect(),
                latest != null && !latest.isCorrect() && attempts.size() < 3,
                attempts.stream().map(this::attemptResponse).toList(),
                correctAnswer,
                answered ? question.getEvidenceText() : null,
                answered ? question.getExplanationOrigin() : null,
                answered ? question.getExplanationLearning() : null
        );
    }

    private PracticeAttemptResponseDto attemptResponse(PracticeAttempt attempt) {
        return new PracticeAttemptResponseDto(
                attempt.getId(),
                attempt.getAttemptNo(),
                jsonCodec.read(attempt.getAnswerJson(), new TypeReference<List<String>>() {
                }),
                attempt.isCorrect(),
                attempt.isOfficial(),
                attempt.getSubmittedAt()
        );
    }

    private BusinessException notFound() {
        return new BusinessException(
                "Reading/Vocabulary 학습 세트를 찾을 수 없습니다.",
                LanguageLearningErrorCode.DAILY_SET_NOT_FOUND
        );
    }
}
