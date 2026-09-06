package jp.co.translacat.domain.languagelearning.daily.validator;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DailyWritingGeneratedItemDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiDailyWritingGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.global.exception.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyWritingGenerationResponseValidatorTest {

    private final DailyWritingGenerationResponseValidator validator =
            new DailyWritingGenerationResponseValidator();

    @Test
    void acceptsExpectedDifficultyDistributionForFreeWriting() {
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
                distribution,
                DailyWritingType.FREE
        )).doesNotThrowAnyException();
    }

    @Test
    void acceptsGuidedWritingOnlyWhenAllGuidanceSectionsArePresent() {
        DifficultyDistributionDto distribution =
                new DifficultyDistributionDto(0, 1, 0);
        AiDailyWritingGenerationResponseDto response =
                new AiDailyWritingGenerationResponseDto(
                        "request-1",
                        "prompt-v1",
                        List.of(guidedItem(1, DailyWritingDifficulty.NORMAL))
                );

        assertThatCode(() -> validator.validate(
                response,
                1,
                distribution,
                DailyWritingType.GUIDED
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsGuidedWritingWhenAnyGuidanceSectionIsMissing() {
        DifficultyDistributionDto distribution =
                new DifficultyDistributionDto(0, 1, 0);
        DailyWritingGeneratedItemDto invalid = new DailyWritingGeneratedItemDto(
                1,
                DailyWritingDifficulty.NORMAL,
                "안내에 따라 메일을 작성하세요.",
                List.of(),
                List.of(),
                "focus",
                List.of("배송이 하루 늦어진다."),
                List.of(),
                List.of("정중한 표현을 사용한다."),
                null,
                null
        );
        AiDailyWritingGenerationResponseDto response =
                new AiDailyWritingGenerationResponseDto(
                        "request-1",
                        "prompt-v1",
                        List.of(invalid)
                );

        assertThatThrownBy(() -> validator.validate(
                response,
                1,
                distribution,
                DailyWritingType.GUIDED
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsGuidanceFieldsForTranslationAndFreeWriting() {
        DifficultyDistributionDto distribution =
                new DifficultyDistributionDto(0, 1, 0);
        AiDailyWritingGenerationResponseDto response =
                new AiDailyWritingGenerationResponseDto(
                        "request-1",
                        "prompt-v1",
                        List.of(guidedItem(1, DailyWritingDifficulty.NORMAL))
                );

        assertThatThrownBy(() -> validator.validate(
                response,
                1,
                distribution,
                DailyWritingType.TRANSLATION
        )).isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> validator.validate(
                response,
                1,
                distribution,
                DailyWritingType.FREE
        )).isInstanceOf(BusinessException.class);
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
                distribution,
                DailyWritingType.FREE
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
                distribution,
                DailyWritingType.FREE
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

    private DailyWritingGeneratedItemDto guidedItem(
            int order,
            DailyWritingDifficulty difficulty
    ) {
        return new DailyWritingGeneratedItemDto(
                order,
                difficulty,
                "안내에 따라 메일을 작성하세요.",
                List.of(),
                List.of(),
                "focus",
                List.of("배송이 하루 늦어진다."),
                List.of("고객에게 지연을 알린다."),
                List.of("2~3문장으로 정중하게 작성한다."),
                null,
                null
        );
    }
}
