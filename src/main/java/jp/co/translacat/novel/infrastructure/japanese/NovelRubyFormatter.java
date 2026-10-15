package jp.co.translacat.novel.infrastructure.japanese;

import jp.co.translacat.infrastructure.japanese.FuriganaProcessor;
import jp.co.translacat.novel.domain.SourceEpisode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 원문과 번역 identity를 건드리지 않고, 일본어 화면 응답에만 Sudachi 발음을 더한다. */
@Component
public class NovelRubyFormatter {
    private static final Pattern GENERATED_RUBY = Pattern.compile("<ruby>([^<>]+)<rt>([^<>]*)</rt></ruby>");
    private static final int MAX_CACHE_WEIGHT = 1_000_000;
    private static final int MAX_RESPONSE_TOKENS = 5_000;
    private final FuriganaProcessor processor;
    private final Map<CacheKey, Cached> cache = new LinkedHashMap<>(128, 0.75f, true);
    private int cacheWeight;

    private record CacheKey(long dictionaryRevision, String raw) {}
    private record Cached(List<SourceEpisode.RubyToken> tokens, int weight) {}

    public NovelRubyFormatter(FuriganaProcessor processor) {
        this.processor = processor;
    }

    public String markup(String raw) {
        if (raw == null || raw.isEmpty()) return raw == null ? "" : raw;
        List<SourceEpisode.RubyToken> tokens = tokens(raw);
        StringBuilder result = new StringBuilder(raw.length() + tokens.size() * 30);
        int offset = 0;
        for (var token : tokens) {
            escape(result, raw.substring(offset, token.startOffset()));
            result.append("<ruby>");
            escape(result, token.text());
            result.append("<rt>");
            escape(result, token.reading());
            result.append("</rt></ruby>");
            offset = token.endOffset();
        }
        escape(result, raw.substring(offset));
        // 목록/본문 FE 계약의 문자열 길이 상한을 넘으면 안전한 원문 표기로 되돌린다.
        return result.length() <= 100_000 ? result.toString() : escaped(raw);
    }

    public List<SourceEpisode.RubyToken> tokens(String raw, List<SourceEpisode.RubyToken> authored) {
        if (raw == null || raw.isEmpty()) return List.of();
        if (authored == null || authored.isEmpty()) return tokens(raw);

        // 출처가 직접 지정한 발음은 유지하고 빈 구간만 Sudachi로 보충한다.
        List<SourceEpisode.RubyToken> result = new ArrayList<>();
        int offset = 0;
        for (var token : authored) {
            if (token == null || token.startOffset() < offset || token.endOffset() > raw.length()
                    || token.endOffset() <= token.startOffset() || token.reading() == null
                    || token.reading().isEmpty() || token.reading().length() > 500
                    || !raw.substring(token.startOffset(), token.endOffset()).equals(token.text())) {
                return tokens(raw);
            }
            appendGap(result, raw.substring(offset, token.startOffset()), offset);
            result.add(token);
            offset = token.endOffset();
        }
        appendGap(result, raw.substring(offset), offset);
        return result.size() <= MAX_RESPONSE_TOKENS ? List.copyOf(result) : List.copyOf(authored);
    }

    public List<SourceEpisode.RubyToken> tokens(String raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        CacheKey key = new CacheKey(processor.dictionaryRevision(), raw);
        synchronized (cache) {
            Cached cached = cache.get(key);
            if (cached != null) return cached.tokens();
        }

        List<SourceEpisode.RubyToken> generated = new ArrayList<>();
        appendGap(generated, raw, 0);
        List<SourceEpisode.RubyToken> result = generated.size() <= MAX_RESPONSE_TOKENS
                ? List.copyOf(generated) : List.of();
        int weight = raw.length() + result.stream().mapToInt(token -> 48 + token.text().length() + token.reading().length()).sum();
        if (weight > MAX_CACHE_WEIGHT || processor.dictionaryRevision() != key.dictionaryRevision()) return result;
        synchronized (cache) {
            // 최대 보유량을 넘으면 오래된 화면 표기를 버린다. 원문/번역 저장소에는 영향이 없다.
            while (cacheWeight + weight > MAX_CACHE_WEIGHT && !cache.isEmpty()) {
                var oldest = cache.entrySet().iterator().next();
                cacheWeight -= oldest.getValue().weight();
                cache.remove(oldest.getKey());
            }
            if (!cache.containsKey(key)) {
                cache.put(key, new Cached(result, weight));
                cacheWeight += weight;
            }
        }
        return result;
    }

    private void appendGap(List<SourceEpisode.RubyToken> output, String text, int baseOffset) {
        if (text.isEmpty()) return;
        // 외부 원문 속 HTML처럼 보이는 글자는 Sudachi의 ruby 태그와 절대 섞지 않는다.
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || isBoundary(text.charAt(i))) {
                if (i > start) output.addAll(generated(text.substring(start, i), baseOffset + start));
                start = i + 1;
            }
        }
    }

    private boolean isBoundary(char value) {
        return "<>&\r\n。！？!?".indexOf(value) >= 0;
    }

    private List<SourceEpisode.RubyToken> generated(String safeText, int baseOffset) {
        String markup = processor.convertToRuby(safeText);
        Matcher matcher = GENERATED_RUBY.matcher(markup);
        StringBuilder reconstructed = new StringBuilder(safeText.length());
        List<SourceEpisode.RubyToken> result = new ArrayList<>();
        int previous = 0;
        while (matcher.find()) {
            reconstructed.append(markup, previous, matcher.start());
            String surface = matcher.group(1);
            String reading = matcher.group(2);
            int start = baseOffset + reconstructed.length();
            reconstructed.append(surface);
            if (!reading.isEmpty() && reading.length() <= 500) {
                result.add(new SourceEpisode.RubyToken(start, start + surface.length(), surface, reading));
            }
            previous = matcher.end();
        }
        reconstructed.append(markup, previous, markup.length());
        // 예상하지 못한 엔진 출력으로 원문과 FE의 offset이 달라지면 발음만 버린다.
        return reconstructed.toString().equals(safeText) ? result : List.of();
    }

    private String escaped(String raw) {
        StringBuilder result = new StringBuilder(raw.length());
        escape(result, raw);
        return result.toString();
    }

    private void escape(StringBuilder result, String text) {
        for (int i = 0; i < text.length(); i++) {
            switch (text.charAt(i)) {
                case '&' -> result.append("&amp;");
                case '<' -> result.append("&lt;");
                case '>' -> result.append("&gt;");
                default -> result.append(text.charAt(i));
            }
        }
    }
}
