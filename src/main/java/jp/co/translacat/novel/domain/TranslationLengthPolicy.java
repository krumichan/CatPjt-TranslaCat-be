package jp.co.translacat.novel.domain;

/** 원문 대상량으로 한 번 선택한 불변 정책을 계획·캐시·계측이 공유한다. 운영 기본값은 FIXED다. */
public final class TranslationLengthPolicy {
    public static final String VERSION = "eligible-cp-le1000-n2c2-gt1000-n8c8-v1";
    public static final String PROFILE = "gpt-6.1-sol|sol-6.1-low-v1|2026-10-04.1|low|32768|default";

    public record Resolution(String sourceRevision, TranslationOptions options, int sourceCodePoints,
                             String policyVersion, String band) {}

    private TranslationLengthPolicy() {}

    public static Resolution resolve(SourceEpisode source, TranslationOptions base) {
        int size = source.segments().stream().mapToInt(segment -> SourceText.eligibleCodePoints(segment.plainJa())).sum();
        if (base.lengthPolicy() == TranslationOptions.LengthPolicy.FIXED) {
            return new Resolution(source.revision(), base, size, "fixed-v1", "FIXED");
        }

        // 동결한 기술 조건과 다른 설정을 같은 선택 정책 이름으로 실행하지 않는다.
        if (!PROFILE.equals(base.profileIdentity()) || base.maxOutputTokens() != 32768
                || base.strategy() != TranslationOptions.Strategy.SEMANTIC
                || base.context() != TranslationOptions.Context.CONTIGUOUS_WIDE_V2
                || base.segmentationPolicy() != SentenceSegmenter.Policy.LEGACY_V1
                || base.responseShape() != TranslationOptions.ResponseShape.KEYED_V4
                || base.validationPolicy() != TranslationOptions.ValidationPolicy.MINIMUM_LETTER_DIGIT_V2
                || base.annotationPolicy() != TranslationOptions.AnnotationPolicy.SOURCE_RUBY_V1) {
            throw new NovelProblem("LENGTH_POLICY_CONFIGURATION_MISMATCH", 503);
        }

        // 1000은 검증 후보의 명시 경계다. 경계 주변의 번역 품질을 실측한 최적값이라는 뜻은 아니다.
        boolean shortSource = size <= 1000;
        int requested = shortSource ? 2 : 8;
        var options = new TranslationOptions(requested, requested, base.strategy(), base.context(),
                base.maxOutputTokens(), base.profileIdentity(), base.responseShape(), base.segmentationPolicy(),
                base.validationPolicy(), base.annotationPolicy(), base.lengthPolicy());
        return new Resolution(source.revision(), options, size, VERSION, shortSource ? "UP_TO_1000" : "ABOVE_1000");
    }
}
