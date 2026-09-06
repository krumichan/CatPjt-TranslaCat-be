package jp.co.translacat.domain.languagelearning.listening.daily.validator;

import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningLearningMode;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class ListeningGenerationResponseValidator {

    public void validate(
            AiListeningContract.GenerationResponse response,
            String requestId,
            String policyVersion,
            String modelConfigVersion,
            int expectedCount,
            ListeningLearningMode learningMode,
            double minAudioSeconds,
            double maxAudioSeconds
    ) {
        if (response == null
                || !requestId.equals(response.requestId())
                || response.items() == null
                || response.items().size() != expectedCount
                || blank(response.generationVersion())
                || !policyVersion.equals(response.policyVersion())
                || !modelConfigVersion.equals(response.modelConfigVersion())) {
            throw invalid("Listening 생성 응답의 기본 계약이 올바르지 않습니다.");
        }
        Set<Integer> indexes = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        for (AiListeningContract.GeneratedItem item : response.items()) {
            if (item == null
                    || item.itemIndex() < 1
                    || item.itemIndex() > expectedCount
                    || !indexes.add(item.itemIndex())
                    || blank(item.sourceText())
                    || item.sourceText().length() > 4000
                    || blank(item.normalizedSourceText())
                    || item.normalizedSourceText().length() > 4000
                    || blank(item.contentHash())
                    || !hashes.add(item.contentHash())
                    || blank(item.similarityKey())
                    || !sized(item.referenceMeanings(), 2, 3)
                    || !sized(item.keyMeaningUnits(), 1, 30)
                    || !sized(item.targetKeywords(), 0, 20)
                    || item.estimatedAudioSeconds() < minAudioSeconds
                    || item.estimatedAudioSeconds() > maxAudioSeconds
                    || item.safety() == null
                    || !item.safety().passed()
                    || !validModePayload(item, learningMode)) {
                throw invalid("Listening 생성 문항 계약이 올바르지 않습니다.");
            }
        }
    }

    private boolean validModePayload(
            AiListeningContract.GeneratedItem item,
            ListeningLearningMode mode
    ) {
        if (mode == null) {
            return false;
        }
        return switch (mode) {
            case DICTATION -> blank(item.question())
                    && (item.options() == null || item.options().isEmpty())
                    && blank(item.correctOptionKey())
                    && blank(item.comprehensionFocus())
                    && (item.summaryKeyPoints() == null || item.summaryKeyPoints().isEmpty());
            case COMPREHENSION -> !blank(item.question())
                    && item.options() != null
                    && item.options().size() == 4
                    && item.options().stream().allMatch(option -> option != null
                            && !blank(option.key()) && !blank(option.text()))
                    && item.options().stream()
                    .map(AiListeningContract.ChoiceOption::key)
                    .collect(java.util.stream.Collectors.toSet())
                    .equals(Set.of("A", "B", "C", "D"))
                    && !blank(item.correctOptionKey())
                    && item.options().stream().anyMatch(option -> option.key().equals(item.correctOptionKey()))
                    && Set.of("GIST", "DETAIL", "INTENT", "INFERENCE", "NEXT_ACTION")
                    .contains(item.comprehensionFocus())
                    && (item.summaryKeyPoints() == null || item.summaryKeyPoints().isEmpty());
            case SUMMARY -> sized(item.summaryKeyPoints(), 2, 6)
                    && blank(item.question())
                    && (item.options() == null || item.options().isEmpty())
                    && blank(item.correctOptionKey())
                    && blank(item.comprehensionFocus());
        };
    }

    private BusinessException invalid(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.AI_SCHEMA_INVALID
        );
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean sized(
            java.util.List<String> values,
            int min,
            int max
    ) {
        return values != null
                && values.size() >= min
                && values.size() <= max
                && values.stream().noneMatch(this::blank);
    }
}
