package jp.co.translacat.domain.languagelearning.level.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestInternalAnswerKeyDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestOptionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiLevelTestQuestionResponseDto;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestAnswerMode;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;
import jp.co.translacat.domain.languagelearning.level.policy.LevelTestRecipe;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityMetadata;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LevelTestQuestionServiceInstructionLanguageContractTest {

    private static final int QUESTION_NUMBER = 4;
    private static final int COMPLEXITY_BAND = 3;

    private final LevelTestQuestionService service = mock(
            LevelTestQuestionService.class,
            CALLS_REAL_METHODS
    );

    @Test
    void batchResponseAcceptsInstructionInLearningLanguage() {
        AiLevelTestQuestionResponseDto response = response(0L);

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(
                service,
                "validateBatchResponse",
                response,
                expectedRecipeEntry(),
                "ko",
                "ja",
                QUESTION_NUMBER,
                COMPLEXITY_BAND
        )).doesNotThrowAnyException();
    }

    @Test
    void liveResponseAcceptsInstructionInLearningLanguage() {
        LevelTestSession session = mock(LevelTestSession.class);
        when(session.getId()).thenReturn(100L);
        when(session.getOriginLanguage()).thenReturn("ko");
        when(session.getLearningLanguage()).thenReturn("ja");

        AiLevelTestQuestionResponseDto response = response(100L);

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(
                service,
                "validateResponse",
                response,
                session,
                expectedRecipeEntry(),
                QUESTION_NUMBER,
                COMPLEXITY_BAND
        )).doesNotThrowAnyException();
    }

    private LevelTestRecipe.Entry expectedRecipeEntry() {
        return new LevelTestRecipe.Entry(
                QUESTION_NUMBER,
                LevelTestDomain.GRAMMAR,
                LevelTestItemType.GRAMMAR_FORM_CHOICE
        );
    }

    private AiLevelTestQuestionResponseDto response(Long sessionId) {
        return new AiLevelTestQuestionResponseDto(
                "instruction-language-contract-test",
                sessionId,
                QUESTION_NUMBER,
                LevelTestRecipe.TOTAL_QUESTIONS,
                LevelTestDomain.GRAMMAR,
                LevelTestItemType.GRAMMAR_FORM_CHOICE,
                COMPLEXITY_BAND,
                "文脈に最も適切な表現を選んでください。",
                "ja",
                LevelTestAnswerMode.CHOICE,
                null,
                "雨が降ったので、傘を_____。",
                List.of(
                        new LevelTestOptionDto("A", "持って行きました"),
                        new LevelTestOptionDto("B", "置いて行きました"),
                        new LevelTestOptionDto("C", "忘れていました"),
                        new LevelTestOptionDto("D", "閉じていました")
                ),
                new LevelTestInternalAnswerKeyDto(
                        "A",
                        List.of(),
                        "UNIQUE_ANSWER",
                        Map.of("A", 100, "B", 0, "C", 0, "D", 0)
                ),
                Map.of(),
                new DiversityMetadata(
                        "DAILY_LIFE",
                        "UNDERSTAND",
                        "grammar-form-choice",
                        List.of("PAST_TENSE"),
                        List.of(),
                        "문맥에 맞는 문법 형태 선택",
                        false,
                        "instruction-language-content-hash",
                        "instruction-language-similarity-key"
                ),
                null,
                null,
                "level-test-generation-v2",
                "level-test-multiskill-prompt-v9",
                null,
                null,
                null
        );
    }
}
