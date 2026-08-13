package jp.co.translacat.domain.languagelearning.daily.validator;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DailyWritingGeneratedItemDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiDailyWritingGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingDifficulty;
import jp.co.translacat.global.exception.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyWritingGenerationResponseValidatorTest {

    private final DailyWritingGenerationResponseValidator validator =
            new DailyWritingGenerationResponseValidator();

    @Test
    void acceptsExpectedDifficultyDistribution() {
        DifficultyDistributionDto distribution =
                new DifficultyDistributionDto(1, 3, 1);
        AiDailyWritingGenerationResponseDto response = response(
                DailyWritingDifficulty.REVIEW,
                DailyWritingDifficulty.NORMAL,
                DailyWritingDifficulty.NORMAL,
                DailyWritingDifficulty.NORMAL,
                DailyWritingDifficulty.CHALLENGE
        );

        assertThatCode(() -> validator.validate(
                response,
                5,
                distribution
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsDuplicatedOrder() {
        DifficultyDistributionDto distribution =
                new DifficultyDistributionDto(1, 1, 0);
        AiDailyWritingGenerationResponseDto response =
                new AiDailyWritingGenerationResponseDto(
                        "request-1",
                        "prompt-v1",
                        List.of(
                                item(1, DailyWritingDifficulty.REVIEW),
                                item(1, DailyWritingDifficulty.NORMAL)
                        )
                );

        assertThatThrownBy(() -> validator.validate(
                response,
                2,
                distribution
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsUnexpectedDifficultyDistribution() {
        DifficultyDistributionDto distribution =
                new DifficultyDistributionDto(1, 3, 1);
        AiDailyWritingGenerationResponseDto response = response(
                DailyWritingDifficulty.REVIEW,
                DailyWritingDifficulty.REVIEW,
                DailyWritingDifficulty.NORMAL,
                DailyWritingDifficulty.NORMAL,
                DailyWritingDifficulty.CHALLENGE
        );

        assertThatThrownBy(() -> validator.validate(
                response,
                5,
                distribution
        )).isInstanceOf(BusinessException.class);
    }

    private AiDailyWritingGenerationResponseDto response(
            DailyWritingDifficulty... difficulties
    ) {
        List<DailyWritingGeneratedItemDto> items =
                java.util.stream.IntStream.range(0, difficulties.length)
                        .mapToObj(index -> item(
                                index + 1,
                                difficulties[index]
                        ))
                        .toList();

        return new AiDailyWritingGenerationResponseDto(
                "request-1",
                "prompt-v1",
                items
        );
    }

    private DailyWritingGeneratedItemDto item(
            int order,
            DailyWritingDifficulty difficulty
    ) {
        return new DailyWritingGeneratedItemDto(
                order,
                difficulty,
                "문장 " + order,
                List.of(),
                List.of(),
                "focus"
        );
    }
}
