package jp.co.translacat.domain.languagelearning.ai.dto;

import com.fasterxml.jackson.databind.ObjectMapper;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.WritingEvaluationScoresDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiDailyWritingGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.common.enums.KeywordSource;
import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageLearningAiContractTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    @Test
    void dailyGenerationContractUsesAi3CamelCaseFields() throws Exception {
        AiDailyWritingGenerationRequestDto request =
                new AiDailyWritingGenerationRequestDto(
                        "req-1",
                        "ko",
                        "ja",
                        5,
                        new DifficultyDistributionDto(1, 3, 1),
                        List.of(new SelectedKeywordDto(
                                "CUSTOM:1",
                                "IT",
                                KeywordSource.CUSTOM,
                                KeywordType.TOPIC,
                                "it",
                                0.5
                        )),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        LocalDate.of(2026, 8, 12),
                        "snap-1"
                );

        String json = objectMapper.writeValueAsString(request);

        assertThat(json)
                .contains("\"requestId\":\"req-1\"")
                .contains("\"sentenceCount\":5")
                .contains("\"difficultyDistribution\"")
                .contains("\"selectedKeywords\"")
                .contains("\"canonicalKey\":\"it\"")
                .contains("\"selectionWeight\":0.5");
    }

    @Test
    void evaluationScoresCarryAllFiveMetrics() {
        WritingEvaluationScoresDto scores =
                new WritingEvaluationScoresDto(
                        77,
                        80,
                        70,
                        75,
                        82,
                        78
                );

        assertThat(scores.meaning()).isEqualTo(80);
        assertThat(scores.expression()).isEqualTo(78);
    }
}
