package jp.co.translacat.novel.application;

import jp.co.translacat.novel.domain.TranslationOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 실험 설정은 Novel에만 속한다. 공통 AI tier나 LL/CHAT 설정을 변경하지 않는다. */
@Component
public record NovelExecutionSettings(int requestedChunks, int requestedConcurrency, String strategy, String context,
                                     String responseShape, String segmentationPolicy, String validationPolicy, String annotationPolicy,
                                     String lengthPolicy) {
    @org.springframework.beans.factory.annotation.Autowired
    public NovelExecutionSettings(@Value("${novel.translation.requested-chunks:0}") int requestedChunks,
                                   @Value("${novel.translation.concurrency:2}") int requestedConcurrency,
                                   @Value("${novel.translation.planner:LEGACY}") String strategy,
                                   @Value("${novel.translation.context:CURRENT}") String context,
                                   @Value("${novel.translation.response-shape:ARRAY_V2}") String responseShape,
                                   @Value("${novel.source.segmentation:LEGACY_V1}") String segmentationPolicy,
                                   @Value("${novel.translation.validation-policy:BASIC_V1}") String validationPolicy,
                                   @Value("${novel.translation.annotation-policy:NONE}") String annotationPolicy,
                                   @Value("${novel.translation.length-policy:FIXED}") String lengthPolicy) {
        this.requestedChunks = requestedChunks; this.requestedConcurrency = requestedConcurrency;
        this.strategy = strategy; this.context = context;
        this.responseShape = responseShape;
        this.segmentationPolicy = segmentationPolicy;
        this.validationPolicy = validationPolicy; this.annotationPolicy = annotationPolicy;
        this.lengthPolicy = lengthPolicy;
        options("configuration-validation", 8192);
    }
    public NovelExecutionSettings(int n, int c, String strategy, String context, String shape, String segmentation,
                                  String validation, String annotation) {
        this(n, c, strategy, context, shape, segmentation, validation, annotation, "FIXED");
    }
    public NovelExecutionSettings(int n, int c, String strategy, String context) { this(n, c, strategy, context, "ARRAY_V2"); }
    public NovelExecutionSettings(int n, int c, String strategy, String context, String shape) { this(n, c, strategy, context, shape, "LEGACY_V1"); }
    public NovelExecutionSettings(int n, int c, String strategy, String context, String shape, String segmentation) {
        this(n, c, strategy, context, shape, segmentation, "BASIC_V1", "NONE");
    }
    public static NovelExecutionSettings baseline() { return new NovelExecutionSettings(0, 2, "LEGACY", "CURRENT"); }
    public TranslationOptions options(NovelPorts.Ai ai) { return options(ai.executionIdentity(), ai.maxOutputTokens()); }
    public TranslationOptions options(String profileIdentity, int maxOutputTokens) {
        return new TranslationOptions(requestedChunks, requestedConcurrency, TranslationOptions.Strategy.valueOf(strategy),
                TranslationOptions.Context.valueOf(context), maxOutputTokens, profileIdentity, TranslationOptions.ResponseShape.valueOf(responseShape),
                jp.co.translacat.novel.domain.SentenceSegmenter.Policy.valueOf(segmentationPolicy),
                TranslationOptions.ValidationPolicy.valueOf(validationPolicy), TranslationOptions.AnnotationPolicy.valueOf(annotationPolicy),
                TranslationOptions.LengthPolicy.valueOf(lengthPolicy));
    }
    public jp.co.translacat.novel.domain.SentenceSegmenter segmenter() {
        return new jp.co.translacat.novel.domain.SentenceSegmenter(jp.co.translacat.novel.domain.SentenceSegmenter.Policy.valueOf(segmentationPolicy));
    }
}
