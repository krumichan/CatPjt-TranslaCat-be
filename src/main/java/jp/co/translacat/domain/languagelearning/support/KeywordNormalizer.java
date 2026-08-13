package jp.co.translacat.domain.languagelearning.support;

import java.text.Normalizer;
import java.util.Locale;

public final class KeywordNormalizer {

    private KeywordNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
