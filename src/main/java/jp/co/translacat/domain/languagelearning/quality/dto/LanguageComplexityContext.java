package jp.co.translacat.domain.languagelearning.quality.dto;

public record LanguageComplexityContext(
        Double baseLevelScore,
        int baseComplexityBand,
        Integer targetComplexityBand,
        String policyVersion
) {
}
