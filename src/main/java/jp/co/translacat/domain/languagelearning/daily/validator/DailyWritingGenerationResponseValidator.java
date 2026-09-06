package jp.co.translacat.domain.languagelearning.daily.validator;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DailyWritingGeneratedItemDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiDailyWritingGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class DailyWritingGenerationResponseValidator {

    public void validate(
            AiDailyWritingGenerationResponseDto response,
            int expectedSentenceCount,
            DifficultyDistributionDto expectedDistribution,
            DailyWritingType writingType
    ) {
        if (writingType == null) {
            throw generationFailure("Daily Writing 유형이 필요합니다.");
        }

        validateResponseHeader(response, expectedSentenceCount);

        int reviewCount = 0;
        int normalCount = 0;
        int challengeCount = 0;
        Set<Integer> orders = new HashSet<>();

        for (DailyWritingGeneratedItemDto item : response.items()) {
            validateItem(item, orders, writingType);

            switch (item.difficulty()) {
                case REVIEW -> reviewCount++;
                case NORMAL -> normalCount++;
                case CHALLENGE -> challengeCount++;
            }
        }

        validateDifficultyDistribution(
                reviewCount,
                normalCount,
                challengeCount,
                expectedDistribution
        );
    }

    private void validateResponseHeader(
            AiDailyWritingGenerationResponseDto response,
            int expectedSentenceCount
    ) {
        boolean invalid = response == null
                || response.items() == null
                || response.items().size() != expectedSentenceCount
                || response.promptVersion() == null;

        if (invalid) {
            throw generationFailure(
                    "AI Daily Writing 응답 개수가 유효하지 않습니다."
            );
        }
    }

    private void validateItem(
            DailyWritingGeneratedItemDto item,
            Set<Integer> orders,
            DailyWritingType writingType
    ) {
        boolean invalid = item == null
                || item.difficulty() == null
                || item.originText() == null
                || item.originText().isBlank()
                || !orders.add(item.order());

        if (invalid) {
            throw generationFailure(
                    "AI Daily Writing 응답 Schema가 유효하지 않습니다."
            );
        }

        validateWritingTypeContract(item, writingType);
    }

    private void validateWritingTypeContract(
            DailyWritingGeneratedItemDto item,
            DailyWritingType writingType
    ) {
        List<String> providedFacts = safe(item.providedFacts());
        List<String> requiredIntents = safe(item.requiredIntents());
        List<String> responseConstraints = safe(item.responseConstraints());

        if (containsBlank(providedFacts)
                || containsBlank(requiredIntents)
                || containsBlank(responseConstraints)) {
            throw generationFailure(
                    "Daily Writing 가이드에 빈 값이 포함되어 있습니다."
            );
        }

        if (writingType == DailyWritingType.GUIDED) {
            if (providedFacts.isEmpty()
                    || requiredIntents.isEmpty()
                    || responseConstraints.isEmpty()) {
                throw generationFailure(
                        "가이드 작문은 제공 사실, 전달 의도, 답변 조건이 모두 필요합니다."
                );
            }
            return;
        }

        if (!providedFacts.isEmpty()
                || !requiredIntents.isEmpty()
                || !responseConstraints.isEmpty()) {
            throw generationFailure(
                    "번역/자유 작문에는 가이드 전용 정보가 포함될 수 없습니다."
            );
        }
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }

    private boolean containsBlank(List<String> values) {
        return values.stream().anyMatch(value -> value == null || value.isBlank());
    }

    private void validateDifficultyDistribution(
            int reviewCount,
            int normalCount,
            int challengeCount,
            DifficultyDistributionDto expected
    ) {
        boolean invalid = reviewCount != expected.review()
                || normalCount != expected.normal()
                || challengeCount != expected.challenge();

        if (invalid) {
            throw generationFailure(
                    "AI가 요청한 난이도 분배를 준수하지 않았습니다."
            );
        }
    }

    private BusinessException generationFailure(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.DAILY_SET_GENERATION_FAILED
        );
    }
}
