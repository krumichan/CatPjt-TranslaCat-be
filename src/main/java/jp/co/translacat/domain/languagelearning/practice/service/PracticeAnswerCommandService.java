package jp.co.translacat.domain.languagelearning.practice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import jp.co.translacat.domain.languagelearning.activity.entity.LearningActivity;
import jp.co.translacat.domain.languagelearning.activity.repository.LearningActivityRepository;
import jp.co.translacat.domain.languagelearning.activity.service.LearningActivityCommandService;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeOptionDto;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeQuestionType;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeSetStatus;
import jp.co.translacat.domain.languagelearning.common.enums.ProfileSignalType;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.practice.dto.request.PracticeAnswerSubmitRequestDto;
import jp.co.translacat.domain.languagelearning.practice.dto.response.PracticeAnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeAttempt;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeMetricScore;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeQuestion;
import jp.co.translacat.domain.languagelearning.practice.entity.VocabularyMastery;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeSet;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeAttemptRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeMetricScoreRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeQuestionRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeSetRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.VocabularyMasteryRepository;
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileSignalService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PracticeAnswerCommandService {
    private static final int MAX_ATTEMPTS = 3;

    private final PracticeQuestionRepository questionRepository;
    private final PracticeSetRepository setRepository;
    private final PracticeAttemptRepository attemptRepository;
    private final PracticeMetricScoreRepository metricRepository;
    private final LanguageLearningJsonCodec jsonCodec;
    private final VocabularyMasteryRepository masteryRepository;
    private final UserRepository userRepository;
    private final LearningActivityCommandService activityCommandService;
    private final LearningActivityRepository activityRepository;
    private final LearningProfileSignalService profileSignalService;

    // Ownership is checked before the set lock; subsequent reads must see commits made while waiting.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PracticeAnswerResultResponseDto submit(
            Long userId,
            Long questionId,
            PracticeAnswerSubmitRequestDto request
    ) {
        PracticeQuestion question = questionRepository
                .findByIdAndPracticeSetUserId(questionId, userId)
                .orElseThrow(this::questionNotFound);
        // Coordinate submissions, completion and generated-question appends on the same set lock.
        PracticeSet set = setRepository.findLockedById(question.getPracticeSet().getId())
                .orElseThrow(this::questionNotFound);
        List<PracticeAttempt> existing = attemptRepository
                .findAllByQuestionIdOrderByAttemptNoAsc(questionId);
        validateCanAnswer(question, request, existing);

        List<String> submitted = normalize(request.answer());
        List<String> correctAnswer = jsonCodec.read(
                question.getCorrectAnswerJson(),
                new TypeReference<List<String>>() {}
        );
        boolean correct = submitted.equals(correctAnswer);
        int attemptNo = existing.size() + 1;
        PracticeAttempt attempt = attemptRepository.save(PracticeAttempt.create(
                question,
                attemptNo,
                jsonCodec.write(submitted),
                correct
        ));

        if (question.getPracticeSet().getDomain() == PracticeDomain.VOCABULARY
                && attemptNo == 1) {
            applyVocabularyMastery(userId, question, correct);
        } else if (attemptNo == 1 && !correct) {
            applyReadingVocabularyCandidates(userId, question);
        }

        boolean setCompleted = finalizeSetIfReady(set);
        return new PracticeAnswerResultResponseDto(
                question.getId(),
                attemptNo,
                correct,
                attempt.isOfficial(),
                setCompleted,
                question.getPracticeSet().getOfficialScore(),
                correctAnswer,
                question.getEvidenceText(),
                question.getExplanationOrigin(),
                question.getExplanationLearning()
        );
    }

    private void validateCanAnswer(
            PracticeQuestion question,
            PracticeAnswerSubmitRequestDto request,
            List<PracticeAttempt> existing
    ) {
        if (request == null || request.answer() == null || request.answer().isEmpty()) {
            throw new BusinessException(
                    "답변이 필요합니다.", LanguageLearningErrorCode.ANSWER_NOT_ALLOWED
            );
        }
        List<String> answer = normalize(request.answer());
        List<String> correct = jsonCodec.read(
                question.getCorrectAnswerJson(), new TypeReference<List<String>>() {}
        );
        List<String> optionKeys = jsonCodec.read(
                question.getOptionsJson(), new TypeReference<List<PracticeOptionDto>>() {}
        ).stream().map(PracticeOptionDto::key).toList();
        if (answer.size() != answer.stream().distinct().count()) {
            throw new BusinessException(
                    "같은 선택지를 중복 제출할 수 없습니다.",
                    LanguageLearningErrorCode.ANSWER_NOT_ALLOWED
            );
        }
        if (question.getQuestionType() == PracticeQuestionType.SINGLE_CHOICE
                && answer.size() != 1) {
            throw new BusinessException(
                    "객관식 답변은 하나만 선택할 수 있습니다.",
                    LanguageLearningErrorCode.ANSWER_NOT_ALLOWED
            );
        }
        if (question.getQuestionType() == PracticeQuestionType.SINGLE_CHOICE
                && !optionKeys.contains(answer.get(0))) {
            throw new BusinessException(
                    "존재하지 않는 선택지입니다.",
                    LanguageLearningErrorCode.ANSWER_NOT_ALLOWED
            );
        }
        if (question.getQuestionType() == PracticeQuestionType.ORDERING
                && (answer.size() != correct.size()
                || answer.size() != optionKeys.size()
                || !new java.util.HashSet<>(answer).equals(new java.util.HashSet<>(optionKeys)))) {
            throw new BusinessException(
                    "모든 조각을 정확히 한 번씩 순서대로 배치해야 합니다.",
                    LanguageLearningErrorCode.ANSWER_NOT_ALLOWED
            );
        }
        if (!existing.isEmpty()) {
            PracticeAttempt latest = existing.get(existing.size() - 1);
            if (latest.isCorrect()) {
                throw new BusinessException(
                        "이미 정답 처리된 문제입니다.",
                        LanguageLearningErrorCode.ANSWER_NOT_ALLOWED
                );
            }
            if (existing.size() >= MAX_ATTEMPTS) {
                throw new BusinessException(
                        "오답 재도전 가능 횟수를 모두 사용했습니다.",
                        LanguageLearningErrorCode.ANSWER_NOT_ALLOWED
                );
            }
        }
    }

    private List<String> normalize(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new BusinessException(
                        "빈 답변은 제출할 수 없습니다.",
                        LanguageLearningErrorCode.ANSWER_NOT_ALLOWED
                );
            }
            result.add(value.trim());
        }
        return List.copyOf(result);
    }


    private void applyReadingVocabularyCandidates(
            Long userId,
            PracticeQuestion question
    ) {
        List<String> candidates = jsonCodec.read(
                question.getVocabularyCandidatesJson(),
                new TypeReference<List<String>>() {}
        );
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        profileSignalService.touchAll(
                userId,
                ProfileSignalType.VOCABULARY_CANDIDATE,
                candidates.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .distinct()
                        .limit(3)
                        .toList()
        );
    }

    private void applyVocabularyMastery(
            Long userId,
            PracticeQuestion question,
            boolean correct
    ) {
        if (question.getCanonicalKey() == null || question.getCanonicalKey().isBlank()) {
            return;
        }
        User user = userRepository.getReferenceById(userId);
        VocabularyMastery mastery = masteryRepository
                .findByUserIdAndCanonicalKey(userId, question.getCanonicalKey())
                .orElseGet(() -> masteryRepository.save(
                        VocabularyMastery.create(user, question.getCanonicalKey(), question.getTargetExpression())
                ));
        mastery.applyScore(correct ? 100 : 0, 0.35);
    }

    private boolean finalizeSetIfReady(PracticeSet set) {
        if (set.getStatus() == PracticeSetStatus.COMPLETED) {
            return true;
        }
        List<PracticeQuestion> questions = questionRepository.findAllByPracticeSetIdOrderByOrderNoAsc(set.getId());
        if (PracticePersistenceService.firstMissingOrder(questions, set.getQuestionCount()) <= set.getQuestionCount()) {
            return false;
        }
        long officialCount = attemptRepository.countByQuestionPracticeSetIdAndAttemptNo(
                set.getId(), 1
        );
        if (officialCount < set.getQuestionCount()) {
            return false;
        }
        long officialCorrect = attemptRepository
                .countByQuestionPracticeSetIdAndAttemptNoAndCorrectTrue(set.getId(), 1);
        double officialScore = round(officialCorrect * 100.0 / set.getQuestionCount());
        set.complete(officialScore);
        createMetrics(set);
        createActivity(set);
        updateProfileSignals(set);
        return true;
    }

    private void createMetrics(PracticeSet set) {
        Map<String, int[]> grouped = new LinkedHashMap<>();
        for (PracticeAttempt attempt : attemptRepository
                .findAllByQuestionPracticeSetIdAndAttemptNoOrderByQuestionOrderNoAsc(
                        set.getId(), 1
                )) {
            int[] counters = grouped.computeIfAbsent(
                    attempt.getQuestion().getSkillTag(), ignored -> new int[2]
            );
            counters[1]++;
            if (attempt.isCorrect()) counters[0]++;
        }
        grouped.forEach((tag, counters) -> metricRepository.save(
                PracticeMetricScore.create(
                        set,
                        tag,
                        round(counters[0] * 100.0 / counters[1]),
                        counters[1]
                )
        ));
    }

    private void createActivity(PracticeSet set) {
        LearningSource source = set.getDomain() == PracticeDomain.READING
                ? LearningSource.READING
                : LearningSource.VOCABULARY;
        long duration = set.getCompletedAt() == null
                ? 0
                : Math.max(0, Duration.between(set.getStartedAt(), set.getCompletedAt()).toSeconds());
        LearningActivity activity = activityCommandService.getOrCreate(
                set.getUser().getId(),
                source,
                String.valueOf(set.getId()),
                set.getLearningDate(),
                (set.getDomain() == PracticeDomain.READING ? "Reading · " : "Vocabulary · ") + set.getMode(),
                duration,
                set.getStartedAt(),
                set.getCompletedAt()
        );
        activity.markEvaluated(set.getOfficialScore(), 1.0);
        activity.updateMetadataJson(jsonCodec.write(Map.of(
                "mode", set.getMode(),
                "questionCount", set.getQuestionCount(),
                "complexityBand", set.getComplexityBand()
        )));
        activityRepository.save(activity);
    }

    private void updateProfileSignals(PracticeSet set) {
        List<String> weaknesses = new ArrayList<>();
        List<String> strengths = new ArrayList<>();
        for (PracticeMetricScore metric : metricRepository
                .findAllByPracticeSetIdOrderBySkillTagAsc(set.getId())) {
            String key = set.getDomain().name() + ":" + metric.getSkillTag();
            if (metric.getScore() < 60) {
                weaknesses.add(key);
            } else if (metric.getScore() >= 85) {
                strengths.add(key);
            }
        }
        Long userId = set.getUser().getId();
        profileSignalService.touchAll(userId, ProfileSignalType.WEAKNESS, weaknesses);
        profileSignalService.touchAll(userId, ProfileSignalType.STRENGTH, strengths);
        if (!weaknesses.isEmpty()) {
            profileSignalService.touchAll(
                    userId,
                    ProfileSignalType.RECOMMENDED_FOCUS,
                    List.of(set.getDomain().name() + ":" + set.getMode())
            );
        }
    }

    private BusinessException questionNotFound() {
        return new BusinessException(
                "Reading/Vocabulary 문제를 찾을 수 없습니다.",
                LanguageLearningErrorCode.DAILY_ITEM_NOT_FOUND
        );
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
