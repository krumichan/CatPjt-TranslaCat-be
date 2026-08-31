package jp.co.translacat.domain.languagelearning.level.service;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LevelTestQuestionServiceResponseTest {

    @Test
    void sentenceOrderDoesNotExposeCompletedSentenceInActiveQuestionResponse() {
        String completedSentence =
                "チームとしては、今後の市場動向を鑑みるに、この新製品の発売時期を延期せざるを得ないとの判断に至った。";

        String exposed = LevelTestQuestionService.promptTextForActiveResponse(
                LevelTestItemType.GRAMMAR_SENTENCE_ORDER,
                completedSentence
        );

        assertThat(exposed).isEmpty();
    }

    @Test
    void otherItemTypesKeepTheirPromptText() {
        String promptText = "この店はいつも混んでいる____、早めに行くことにしています。";

        String exposed = LevelTestQuestionService.promptTextForActiveResponse(
                LevelTestItemType.GRAMMAR_FORM_CHOICE,
                promptText
        );

        assertThat(exposed).isEqualTo(promptText);
    }

    @Test
    void speakingRepeatUsesReferenceAudioWithoutDuplicatingPromptBlock() {
        String exposed = LevelTestQuestionService.promptTextForActiveResponse(
                LevelTestItemType.SPEAKING_REPEAT,
                "音声を聞いて、そのまま繰り返してください。"
        );

        assertThat(exposed).isEmpty();
    }
}
