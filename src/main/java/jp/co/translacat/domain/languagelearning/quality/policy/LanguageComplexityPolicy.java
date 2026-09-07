package jp.co.translacat.domain.languagelearning.quality.policy;

import org.springframework.stereotype.Component;

@Component
public class LanguageComplexityPolicy {

    public static final String VERSION = "language-complexity";

    public int baseBand(Double score) {
        if (score == null) {
            return 2;
        }
        if (score < 40) {
            return 1;
        }
        if (score < 55) {
            return 2;
        }
        if (score < 70) {
            return 3;
        }
        if (score < 85) {
            return 4;
        }
        return 5;
    }

    public int clamp(int band) {
        return Math.max(1, Math.min(5, band));
    }
}
