package jp.co.translacat.novel.domain;

/** 요청 분할 수와 실행 동시성은 별개이며 모든 실험 선택을 캐시 identity에 포함한다. */
public record TranslationOptions(int requestedChunks, int requestedConcurrency, Strategy strategy,
                                 Context context, int maxOutputTokens, String profileIdentity, ResponseShape responseShape,
                                 SentenceSegmenter.Policy segmentationPolicy, ValidationPolicy validationPolicy,
                                 AnnotationPolicy annotationPolicy, LengthPolicy lengthPolicy) {
    public enum Strategy { LEGACY, BALANCED, SEMANTIC }
    public enum Context { CURRENT, SMALL, WIDE, CONTIGUOUS_WIDE_V2 }
    public enum ValidationPolicy { BASIC_V1, MINIMUM_LETTER_DIGIT_V2 }
    public enum AnnotationPolicy { NONE, SOURCE_RUBY_V1 }
    public enum LengthPolicy { FIXED, QUALITY_LENGTH_V1 }
    public enum ResponseShape {
        ARRAY_V2("segment-items-v2-compact"), KEYED_V3("segment-map-v3-compact"), KEYED_V4("segment-map-v4-nonblank");
        private final String schemaVersion;
        ResponseShape(String schemaVersion) { this.schemaVersion = schemaVersion; }
        public String schemaVersion() { return schemaVersion; }
    }
    public static final String VERSION = "novel-plan-v1";

    public TranslationOptions {
        if (responseShape == null) responseShape = ResponseShape.ARRAY_V2;
        if (segmentationPolicy == null) segmentationPolicy = SentenceSegmenter.Policy.LEGACY_V1;
        if (validationPolicy == null) validationPolicy = ValidationPolicy.BASIC_V1;
        if (annotationPolicy == null) annotationPolicy = AnnotationPolicy.NONE;
        if (lengthPolicy == null) lengthPolicy = LengthPolicy.FIXED;
        if (strategy == null || context == null || requestedChunks < 0 || requestedChunks > 16
                || requestedChunks == 0 && strategy != Strategy.LEGACY
                || requestedConcurrency < 1 || requestedConcurrency > 10
                || maxOutputTokens < 1024 || maxOutputTokens > 32768
                || profileIdentity == null || !profileIdentity.matches("[A-Za-z0-9._:|/-]{1,240}")) {
            throw new NovelProblem("TRANSLATION_OPTIONS_INVALID", 422);
        }
    }
    public TranslationOptions(int n, int c, Strategy strategy, Context context, int output, String identity,
                              ResponseShape shape, SentenceSegmenter.Policy segmentation,
                              ValidationPolicy validation, AnnotationPolicy annotation) {
        this(n, c, strategy, context, output, identity, shape, segmentation, validation, annotation, LengthPolicy.FIXED);
    }
    public TranslationOptions(int requestedChunks, int requestedConcurrency, Strategy strategy, Context context,
                               int maxOutputTokens, String profileIdentity) {
        this(requestedChunks, requestedConcurrency, strategy, context, maxOutputTokens, profileIdentity, ResponseShape.ARRAY_V2);
    }
    public TranslationOptions(int n, int c, Strategy strategy, Context context, int output, String identity, ResponseShape shape) {
        this(n, c, strategy, context, output, identity, shape, SentenceSegmenter.Policy.LEGACY_V1);
    }
    public TranslationOptions(int n, int c, Strategy strategy, Context context, int output, String identity,
                              ResponseShape shape, SentenceSegmenter.Policy segmentation) {
        this(n, c, strategy, context, output, identity, shape, segmentation, ValidationPolicy.BASIC_V1, AnnotationPolicy.NONE);
    }

    public static TranslationOptions baseline(String profileIdentity) {
        return new TranslationOptions(0, 2, Strategy.LEGACY, Context.CURRENT, 8192, profileIdentity);
    }

    public String fingerprint() {
        String material = String.join("|", VERSION, Integer.toString(requestedChunks),
                Integer.toString(requestedConcurrency), strategy.name(), context.name(),
                Integer.toString(maxOutputTokens), profileIdentity);
        if (responseShape != ResponseShape.ARRAY_V2) material += "|" + responseShape.schemaVersion();
        if (segmentationPolicy != SentenceSegmenter.Policy.LEGACY_V1) material += "|" + segmentationPolicy.version();
        if (validationPolicy != ValidationPolicy.BASIC_V1) material += "|validation:" + validationPolicy.name();
        if (annotationPolicy != AnnotationPolicy.NONE) material += "|annotation:" + annotationPolicy.name();
        if (lengthPolicy != LengthPolicy.FIXED) material += "|length:" + lengthPolicy.name() + "|" + TranslationLengthPolicy.VERSION;
        return SentenceSegmenter.hash(material);
    }
}
