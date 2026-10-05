package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.domain.SourceEpisode;
import jp.co.translacat.novel.infrastructure.ai.StrictTranslationValidator;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

/** Independent structural probes; no target-language answer or corpus-specific replacement. */
class MinimumTranslationContentTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void sourceWithLettersOrDigitsCannotBeSavedAsOnlyPunctuationOrEmoji() {
        for (String source : List.of("鳥が来た。", "１２３", "١٢٣", "𠮷")) {
            for (String output : List.of("」", "……", "😀", "\u200b")) {
                assertThatThrownBy(() -> validate(source, output)).hasMessage("TRANSLATION_MINIMUM_CONTENT_INVALID");
            }
        }
    }
    @Test void symbolicSourceAndLegitimateNonKoreanOrNumericOutputAreNotLanguageFiltered() {
        for (String[] pair : List.of(new String[]{"……", "…"}, new String[]{"😀", "😺"},
                new String[]{"！！", "!?"}, new String[]{"猫。", "cat"}, new String[]{"猫。", "고양이"},
                new String[]{"１２３", "123"}, new String[]{"١٢٣", "١٢٣"}, new String[]{"𠮷", "𠮷"})) {
            assertThat(validate(pair[0], pair[1])).containsEntry("s0", pair[1]);
        }
    }
    @Test void baselinePreservesItsValidationAndNewGuardIsIdenticalForArrayAndKeyedRestoration() throws Exception {
        String source = "鳥。";
        var segment = new SourceEpisode.Segment("canonical", "p0", 0, 0, 0, source.length(), source, source);
        var batch = jp.co.translacat.novel.infrastructure.ai.CompactTranslationBatch.of(List.of(segment));
        for (var shape : jp.co.translacat.novel.domain.TranslationOptions.ResponseShape.values()) {
            var output = shape == jp.co.translacat.novel.domain.TranslationOptions.ResponseShape.ARRAY_V2
                    ? json.readTree("{\"items\":[{\"id\":\"s0\",\"text\":\"」\"}]}") : json.readTree("{\"s0\":\"」\"}");
            assertThat(batch.restore(output, shape)).containsEntry("canonical", "」");
            assertThatThrownBy(() -> batch.restore(output, shape,
                    jp.co.translacat.novel.domain.TranslationOptions.ValidationPolicy.MINIMUM_LETTER_DIGIT_V2))
                    .hasMessage("TRANSLATION_MINIMUM_CONTENT_INVALID");
        }
    }
    @Test void unicodeOtherNumbersAndCombiningMarksAreNotDecimalDigitsOrLetters() {
        for (String source : List.of("Ⅷ", "²", "\u0301")) assertThat(validate(source, "…")).containsEntry("s0", "…");
        for (String output : List.of("Ⅷ", "²", "\u0301"))
            assertThatThrownBy(() -> validate("八", output)).hasMessage("TRANSLATION_MINIMUM_CONTENT_INVALID");
    }
    private Map<String, String> validate(String source, String translated) {
        var segment = new SourceEpisode.Segment("s0", "p0", 0, 0, 0, source.length(), source, source);
        return new StrictTranslationValidator().validateKeyed(json.valueToTree(Map.of("s0", translated)), List.of(segment),
                jp.co.translacat.novel.domain.TranslationOptions.ValidationPolicy.MINIMUM_LETTER_DIGIT_V2);
    }
}
