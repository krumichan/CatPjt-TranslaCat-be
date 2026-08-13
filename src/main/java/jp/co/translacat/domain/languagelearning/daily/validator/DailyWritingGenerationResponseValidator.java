package jp.co.translacat.domain.languagelearning.daily.validator;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DailyWritingGeneratedItemDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiDailyWritingGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class DailyWritingGenerationResponseValidator {

    public void validate(
            AiDailyWritingGenerationResponseDto response,
            int expectedSentenceCount,
            DifficultyDistributionDto expectedDistribution
    ) {
        validateResponseHeader(response, expectedSentenceCount);

        int reviewCount = 0;
        int normalCount = 0;
        int challengeCount = 0;
        Set<Integer> orders = new HashSet<>();

        for (DailyWritingGeneratedItemDto item : response.items()) {
            validateItem(item, orders);

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
            Set<Integer> orders
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
