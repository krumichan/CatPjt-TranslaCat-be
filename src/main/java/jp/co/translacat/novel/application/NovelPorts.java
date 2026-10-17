package jp.co.translacat.novel.application;

import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.SourceEpisode;

import java.util.List;
import java.util.Map;

public final class NovelPorts {
    private NovelPorts() {}

    public interface Source {
        SourceEpisode fetch(EpisodeKey key);
    }

    public interface Ai {
        String model();
        String speechModel();
        default String executionIdentity() { return model() + "|legacy|none|8192|standard"; }
        default int maxOutputTokens() { return 8192; }
        default boolean progressiveAEnabled() { return false; }
        Translation translate(String traceId, List<SourceEpisode.Segment> items,
                              List<String> context, long remainingMillis);
        default Translation translate(String traceId, List<SourceEpisode.Segment> items,
                                      List<String> context, Map<String, String> glossary, long remainingMillis) {
            return translate(traceId, items, context, remainingMillis);
        }
        default Translation translate(String traceId, String callId, List<SourceEpisode.Segment> items,
                                      List<String> context, Map<String, String> glossary, long remainingMillis) {
            return translate(callId, items, context, glossary, remainingMillis);
        }
        default Translation translate(String traceId, String callId, List<SourceEpisode.Segment> items,
                                      List<String> context, Map<String, String> glossary, long remainingMillis,
                                      jp.co.translacat.novel.domain.TranslationOptions.ResponseShape shape) {
            return translate(traceId, callId, items, context, glossary, remainingMillis);
        }
        default Translation translate(String traceId, String callId, List<SourceEpisode.Segment> items,
                                      List<String> context, Map<String, String> glossary, long remainingMillis,
                                      jp.co.translacat.novel.domain.TranslationOptions.ResponseShape shape,
                                      jp.co.translacat.novel.domain.TranslationOptions.ValidationPolicy validation,
                                      jp.co.translacat.novel.domain.TranslationOptions.AnnotationPolicy annotation) {
            if (annotation != jp.co.translacat.novel.domain.TranslationOptions.AnnotationPolicy.NONE
                    && items.stream().anyMatch(item -> !item.rubyTokens().isEmpty())) {
                throw new jp.co.translacat.novel.domain.NovelProblem("AI_ANNOTATION_POLICY_UNSUPPORTED", 503);
            }
            var translated = translate(traceId, callId, items, context, glossary, remainingMillis, shape);
            jp.co.translacat.novel.domain.TranslationContentGuard.validate(items, translated.items(), validation);
            return translated;
        }
        default Translation translateProgressive(String traceId, String callId, List<SourceEpisode.Segment> items,
                                                 List<String> context, Map<String, String> glossary, long remainingMillis,
                                                 jp.co.translacat.novel.domain.TranslationOptions.ResponseShape shape,
                                                 jp.co.translacat.novel.domain.TranslationOptions.ValidationPolicy validation,
                                                 jp.co.translacat.novel.domain.TranslationOptions.AnnotationPolicy annotation,
                                                 java.util.function.BiConsumer<SourceEpisode.Segment, String> onSentence) {
            throw new jp.co.translacat.novel.domain.NovelProblem("PROGRESSIVE_UNSUPPORTED", 503);
        }
        default Translation translateProgressive(String traceId, String callId, List<SourceEpisode.Segment> items,
                List<String> context, Map<String,String> glossary, long remainingMillis,
                jp.co.translacat.novel.domain.TranslationOptions.ResponseShape shape,
                jp.co.translacat.novel.domain.TranslationOptions.ValidationPolicy validation,
                jp.co.translacat.novel.domain.TranslationOptions.AnnotationPolicy annotation,
                java.util.function.BiConsumer<SourceEpisode.Segment,String> onSentence,
                java.util.function.BiConsumer<SourceEpisode.Segment,String> onFailure) {
            return translateProgressive(traceId, callId, items, context, glossary, remainingMillis, shape, validation, annotation, onSentence);
        }
        default Translation repair(String traceId, String callId, List<SourceEpisode.Segment> items,
                List<String> context, Map<String,String> glossary, List<Map<String,String>> acceptedReferences,
                long remainingMillis, jp.co.translacat.novel.domain.TranslationOptions.ResponseShape shape,
                jp.co.translacat.novel.domain.TranslationOptions.ValidationPolicy validation,
                jp.co.translacat.novel.domain.TranslationOptions.AnnotationPolicy annotation) {
            return translate(traceId, callId, items, context, glossary, remainingMillis, shape, validation, annotation);
        }
        default Translation translate(String traceId, String callId, List<SourceEpisode.Segment> items,
                List<String> context, Map<String,String> glossary, long remainingMillis,
                jp.co.translacat.novel.domain.TranslationOptions.ResponseShape shape,
                jp.co.translacat.novel.domain.TranslationOptions.ValidationPolicy validation,
                jp.co.translacat.novel.domain.TranslationOptions.AnnotationPolicy annotation, String targetLanguage) {
            requireLegacyTarget(targetLanguage);
            return translate(traceId, callId, items, context, glossary, remainingMillis, shape, validation, annotation);
        }
        default Translation repair(String traceId, String callId, List<SourceEpisode.Segment> items,
                List<String> context, Map<String,String> glossary, List<Map<String,String>> acceptedReferences,
                long remainingMillis, jp.co.translacat.novel.domain.TranslationOptions.ResponseShape shape,
                jp.co.translacat.novel.domain.TranslationOptions.ValidationPolicy validation,
                jp.co.translacat.novel.domain.TranslationOptions.AnnotationPolicy annotation, String targetLanguage) {
            requireLegacyTarget(targetLanguage);
            return repair(traceId, callId, items, context, glossary, acceptedReferences, remainingMillis, shape, validation, annotation);
        }
        default Translation translateProgressive(String traceId, String callId, List<SourceEpisode.Segment> items,
                List<String> context, Map<String,String> glossary, long remainingMillis,
                jp.co.translacat.novel.domain.TranslationOptions.ResponseShape shape,
                jp.co.translacat.novel.domain.TranslationOptions.ValidationPolicy validation,
                jp.co.translacat.novel.domain.TranslationOptions.AnnotationPolicy annotation,
                java.util.function.BiConsumer<SourceEpisode.Segment,String> onSentence,
                java.util.function.BiConsumer<SourceEpisode.Segment,String> onFailure, String targetLanguage) {
            requireLegacyTarget(targetLanguage);
            return translateProgressive(traceId, callId, items, context, glossary, remainingMillis, shape, validation, annotation, onSentence, onFailure);
        }
        private static void requireLegacyTarget(String targetLanguage) {
            if (!"ko".equals(jp.co.translacat.novel.domain.NovelTargetLanguage.normalize(targetLanguage)))
                throw new jp.co.translacat.novel.domain.NovelProblem("AI_TARGET_LANGUAGE_UNSUPPORTED", 503);
        }
        default Translation translateTitles(String traceId, String callId, List<SourceEpisode.Segment> items,
                List<String> context, Map<String, String> glossary, List<Map<String, String>> acceptedReferences,
                long remainingMillis, jp.co.translacat.novel.domain.TranslationOptions.ResponseShape shape,
                jp.co.translacat.novel.domain.TranslationOptions.ValidationPolicy validation,
                jp.co.translacat.novel.domain.TranslationOptions.AnnotationPolicy annotation, String targetLanguage) {
            return repair(traceId, callId, items, context, glossary, acceptedReferences, remainingMillis,
                    shape, validation, annotation, targetLanguage);
        }
        default TitleReview reviewTitles(String traceId, String callId, List<TitleCandidate> titles,
                List<TitleReference> references, Map<String, String> glossary, long remainingMillis,
                String targetLanguage) {
            throw new jp.co.translacat.novel.domain.NovelProblem("AI_TITLE_REVIEW_UNSUPPORTED", 503);
        }
        Speech synthesize(String requestId, String text, String language, long remainingMillis);
        default Speech synthesize(String traceId, String callId, String text, String language, long remainingMillis) {
            return synthesize(callId, text, language, remainingMillis);
        }
    }

    public record TitleCandidate(String id, String source, String translation) {}
    public record TitleReference(String source, String translation) {}
    public record TitleReview(Map<String, String> corrections, String provider, String model,
                              long inputTokens, long outputTokens, Map<String, Object> timings) {}

    public record Translation(Map<String, String> items, String provider, String model,
                              long inputTokens, long outputTokens, Map<String, Object> timings) {
        public Translation(Map<String, String> items, String provider, String model, long inputTokens, long outputTokens) {
            this(items, provider, model, inputTokens, outputTokens, Map.of());
        }
    }
    public record Speech(String audioBase64, String contentType, Double durationSeconds,
                         String provider, String model, Map<String, Object> timings) {
        public Speech(String audioBase64, String contentType, Double durationSeconds, String provider, String model) {
            this(audioBase64, contentType, durationSeconds, provider, model, Map.of());
        }
    }
}
