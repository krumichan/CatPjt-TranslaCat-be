package jp.co.translacat.domain.languagelearning.level.policy;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LevelTestRecipe {

    public static final int TOTAL_QUESTIONS = 20;

    private static final List<Entry> ENTRIES = List.of(
            new Entry(1, LevelTestDomain.VOCABULARY, LevelTestItemType.VOCAB_CONTEXT_CHOICE),
            new Entry(2, LevelTestDomain.VOCABULARY, LevelTestItemType.VOCAB_CONTEXT_CHOICE),
            new Entry(3, LevelTestDomain.VOCABULARY, LevelTestItemType.VOCAB_PARAPHRASE_CHOICE),
            new Entry(4, LevelTestDomain.GRAMMAR, LevelTestItemType.GRAMMAR_FORM_CHOICE),
            new Entry(5, LevelTestDomain.GRAMMAR, LevelTestItemType.GRAMMAR_FORM_CHOICE),
            new Entry(6, LevelTestDomain.GRAMMAR, LevelTestItemType.GRAMMAR_SENTENCE_ORDER),
            new Entry(7, LevelTestDomain.READING, LevelTestItemType.READING_GIST),
            new Entry(8, LevelTestDomain.READING, LevelTestItemType.READING_DETAIL),
            new Entry(9, LevelTestDomain.READING, LevelTestItemType.READING_DISCOURSE_FUNCTION),
            new Entry(10, LevelTestDomain.READING, LevelTestItemType.READING_TEXT_INFERENCE),
            new Entry(11, LevelTestDomain.LISTENING, LevelTestItemType.LISTENING_GIST_CHOICE),
            new Entry(12, LevelTestDomain.LISTENING, LevelTestItemType.LISTENING_DETAIL_CHOICE),
            new Entry(13, LevelTestDomain.LISTENING, LevelTestItemType.LISTENING_DICTATION),
            new Entry(14, LevelTestDomain.LISTENING, LevelTestItemType.LISTENING_INTERPRETATION),
            // Writing intentionally favors translation (2/3) so the Level Test
            // primarily measures language ability with low non-linguistic burden.
            new Entry(15, LevelTestDomain.WRITING, LevelTestItemType.WRITING_TRANSLATION),
            new Entry(16, LevelTestDomain.WRITING, LevelTestItemType.WRITING_TRANSLATION),
            new Entry(17, LevelTestDomain.WRITING, LevelTestItemType.WRITING_SHORT_PARAGRAPH),
            // Speaking separates pronunciation-focused repetition from productive speech:
            // Q18 exposes the reference text, Q19 is audio-only with three plays,
            // and Q20 is one guided open response.
            new Entry(18, LevelTestDomain.SPEAKING, LevelTestItemType.SPEAKING_REPEAT),
            new Entry(19, LevelTestDomain.SPEAKING, LevelTestItemType.SPEAKING_REPEAT),
            new Entry(20, LevelTestDomain.SPEAKING, LevelTestItemType.SPEAKING_GUIDED_RESPONSE)
    );

    public Entry entry(int questionNumber) {
        if (questionNumber < 1 || questionNumber > TOTAL_QUESTIONS) {
            throw new IllegalArgumentException(
                    "questionNumber must be between 1 and 20"
            );
        }
        return ENTRIES.get(questionNumber - 1);
    }

    public List<Entry> entries() {
        return ENTRIES;
    }

    public record Entry(
            int questionNumber,
            LevelTestDomain domain,
            LevelTestItemType itemType
    ) {
    }
}
