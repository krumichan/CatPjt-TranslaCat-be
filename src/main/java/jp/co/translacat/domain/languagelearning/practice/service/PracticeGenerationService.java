package jp.co.translacat.domain.languagelearning.practice.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeReviewTargetDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiPracticeGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiPracticeGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.port.LanguageLearningAiClient;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.common.enums.ProfileSignalType;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeQuestionType;
import jp.co.translacat.domain.languagelearning.keyword.model.SelectedKeywordCandidate;
import jp.co.translacat.domain.languagelearning.keyword.service.KeywordCandidateQueryService;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeAttempt;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeSet;
import jp.co.translacat.domain.languagelearning.practice.entity.VocabularyMastery;
import jp.co.translacat.domain.languagelearning.practice.policy.PracticeComplexityPolicy;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeAttemptRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeSetRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.VocabularyMasteryRepository;
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileCommandService;
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileSignalService;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PracticeGenerationService {
    private static final int READING_QUESTION_COUNT = 5;
    private static final int VOCABULARY_QUESTION_COUNT = 10;
    private static final int VOCABULARY_REVIEW_TARGET = 6;

    private final PracticeSetRepository setRepository;
    private final PracticePersistenceService persistenceService;
    private final PracticeComplexityPolicy complexityPolicy;
    private final LanguageLearningAiClient aiClient;
    private final LanguageLearningUserSettingQueryService settingQueryService;
    private final LearningProfileCommandService profileCommandService;
    private final LearningProfileSignalService profileSignalService;
    private final KeywordCandidateQueryService keywordCandidateQueryService;
    private final VocabularyMasteryRepository masteryRepository;
    private final PracticeAttemptRepository attemptRepository;

    public PracticeSet getOrGenerate(
            Long userId,
            PracticeDomain domain,
            String mode
    ) {
        validateMode(domain, mode);
        LanguageLearningUserSetting setting = settingQueryService.getOrCreateEntity(userId);
        settingQueryService.requireConfigured(setting);
        LocalDate today = settingQueryService.resolveToday(setting);
        profileCommandService.prepareDailyLearning(userId, today);

        var existing = setRepository.findByUserIdAndLearningDateAndDomainAndMode(
                userId, today, domain, mode
        );
        if (existing.isPresent()) {
            return existing.get();
        }

        int questionCount = domain == PracticeDomain.READING
                ? READING_QUESTION_COUNT
                : VOCABULARY_QUESTION_COUNT;
        int complexityBand = complexityPolicy.resolve(userId, domain, mode);
        int[] mix = complexityPolicy.mix(domain);
        List<PracticeReviewTargetDto> reviewTargets = domain == PracticeDomain.VOCABULARY
                ? reviewTargets(userId)
                : List.of();
        int reviewQuestionCount = Math.min(VOCABULARY_REVIEW_TARGET, reviewTargets.size());

        AiPracticeGenerationRequestDto request = new AiPracticeGenerationRequestDto(
                "practice-" + userId + "-" + today + "-" + domain + "-" + mode + "-" + UUID.randomUUID(),
                domain,
                mode,
                setting.getOriginLanguage(),
                setting.getLearningLanguage(),
                questionCount,
                complexityBand,
                mix[0],
                mix[1],
                mix[2],
                selectedKeywords(userId, today),
                weakSignals(userId),
                recentMistakes(userId, domain),
                reviewTargets,
                reviewQuestionCount,
                today
        );
        AiPracticeGenerationResponseDto generated = aiClient.generatePractice(request);
        validateGenerated(request, generated);

        return persistenceService.persist(
                userId,
                today,
                setting.getOriginLanguage(),
                setting.getLearningLanguage(),
                domain,
                mode,
                questionCount,
                complexityBand,
                generated
        );
    }

    private List<String> selectedKeywords(Long userId, LocalDate today) {
        return keywordCandidateQueryService.findCandidates(userId, today).stream()
                .sorted(Comparator.comparingDouble(SelectedKeywordCandidate::rawWeight).reversed())
                .limit(8)
                .map(candidate -> candidate.keyword().text())
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private List<String> weakSignals(Long userId) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.addAll(profileSignalService.getKeys(userId, ProfileSignalType.WEAKNESS, 8));
        values.addAll(profileSignalService.getKeys(userId, ProfileSignalType.RECOMMENDED_FOCUS, 8));
        return values.stream().limit(12).toList();
    }

    private List<String> recentMistakes(Long userId, PracticeDomain domain) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (PracticeAttempt attempt : attemptRepository
                .findTop30ByQuestionPracticeSetUserIdAndQuestionPracticeSetDomainAndCorrectFalseOrderBySubmittedAtDesc(
                        userId, domain
                )) {
            var question = attempt.getQuestion();
            String detail = question.getTargetExpression() == null
                    ? question.getSkillTag()
                    : question.getSkillTag() + ":" + question.getTargetExpression();
            result.add(detail);
            if (result.size() >= 12) break;
        }
        return List.copyOf(result);
    }

    private List<PracticeReviewTargetDto> reviewTargets(Long userId) {
        List<PracticeReviewTargetDto> result = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        Map<String, ReviewEvidence> evidenceByKey = recentVocabularyEvidence(userId);

        for (String candidate : profileSignalService.getKeys(
                userId, ProfileSignalType.VOCABULARY_CANDIDATE, 12
        )) {
            String canonical = normalizeCandidate(candidate);
            if (canonical == null || !seen.add(canonical)) continue;
            ReviewEvidence evidence = evidenceByKey.getOrDefault(canonical, ReviewEvidence.empty());
            result.add(new PracticeReviewTargetDto(
                    canonical,
                    candidate.trim(),
                    null,
                    Math.max(1, evidence.wrongCount()),
                    evidence.previousQuestionTypes()
            ));
            if (result.size() >= 12) return List.copyOf(result);
        }

        for (VocabularyMastery mastery : masteryRepository.findAllByUserIdOrderByScoreAsc(userId)) {
            if (mastery.getEvaluationCount() <= 0) continue;
            String canonical = normalizeCandidate(mastery.getCanonicalKey());
            if (canonical == null || !seen.add(canonical)) continue;
            ReviewEvidence evidence = evidenceByKey.getOrDefault(canonical, ReviewEvidence.empty());
            result.add(new PracticeReviewTargetDto(
                    mastery.getCanonicalKey(),
                    mastery.getDisplayExpression(),
                    mastery.getScore(),
                    Math.max(evidence.wrongCount(), mastery.getScore() < 55 ? 2 : 1),
                    evidence.previousQuestionTypes()
            ));
            if (result.size() >= 12) break;
        }
        return List.copyOf(result);
    }

    private Map<String, ReviewEvidence> recentVocabularyEvidence(Long userId) {
        Map<String, Integer> wrongCounts = new java.util.LinkedHashMap<>();
        Map<String, LinkedHashSet<PracticeQuestionType>> questionTypes = new java.util.LinkedHashMap<>();
        for (PracticeAttempt attempt : attemptRepository
                .findTop30ByQuestionPracticeSetUserIdAndQuestionPracticeSetDomainAndCorrectFalseOrderBySubmittedAtDesc(
                        userId, PracticeDomain.VOCABULARY
                )) {
            var question = attempt.getQuestion();
            String canonical = normalizeCandidate(question.getCanonicalKey());
            if (canonical == null) continue;
            wrongCounts.merge(canonical, 1, Integer::sum);
            questionTypes.computeIfAbsent(canonical, ignored -> new LinkedHashSet<>())
                    .add(question.getQuestionType());
        }
        Map<String, ReviewEvidence> result = new java.util.LinkedHashMap<>();
        for (String canonical : wrongCounts.keySet()) {
            result.put(canonical, new ReviewEvidence(
                    wrongCounts.getOrDefault(canonical, 0),
                    List.copyOf(questionTypes.getOrDefault(canonical, new LinkedHashSet<>()))
            ));
        }
        return result;
    }

    private String normalizeCandidate(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private record ReviewEvidence(
            int wrongCount,
            List<PracticeQuestionType> previousQuestionTypes
    ) {
        static ReviewEvidence empty() {
            return new ReviewEvidence(0, List.of());
        }
    }

    private void validateGenerated(
            AiPracticeGenerationRequestDto request,
            AiPracticeGenerationResponseDto response
    ) {
        if (response == null
                || response.domain() != request.domain()
                || !request.mode().equals(response.mode())
                || response.questions() == null
                || response.questions().size() != request.questionCount()) {
            throw new BusinessException(
                    "Reading/Vocabulary AI 응답 계약이 올바르지 않습니다.",
                    LanguageLearningErrorCode.AI_SCHEMA_INVALID
            );
        }
    }

    private void validateMode(PracticeDomain domain, String mode) {
        try {
            if (domain == PracticeDomain.READING) {
                jp.co.translacat.domain.languagelearning.common.enums.ReadingMode.valueOf(mode);
            } else {
                jp.co.translacat.domain.languagelearning.common.enums.VocabularyMode.valueOf(mode);
            }
        } catch (Exception e) {
            throw new BusinessException(
                    "Reading/Vocabulary 학습 형식이 올바르지 않습니다.",
                    LanguageLearningErrorCode.SETTING_INVALID
            );
        }
    }
}
