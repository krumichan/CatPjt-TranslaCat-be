package jp.co.translacat.novel.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class SentenceSegmenter {
    public static final String VERSION = "ja-boundaries-v1";
    public enum Policy {
        LEGACY_V1(VERSION), QUOTE_V2("ja-boundaries-v2-quotes");
        private final String version;
        Policy(String version) { this.version = version; }
        public String version() { return version; }
    }
    public static final int MAX_SOURCE_CHARS = 100_000;
    private static final int MAX_UNIT_CHARS = 1500;
    private final Policy policy;
    public SentenceSegmenter() { this(Policy.LEGACY_V1); }
    public SentenceSegmenter(Policy policy) { this.policy = java.util.Objects.requireNonNull(policy); }

    public SourceEpisode segment(EpisodeKey key, String title, List<String> paragraphs,
                                 String previous, String next) {
        return segment(key, title, paragraphs, previous, next, List.of());
    }

    public SourceEpisode segment(EpisodeKey key, String title, List<String> paragraphs,
                                 String previous, String next, List<List<SourceEpisode.RubyToken>> ruby) {
        // 길이 표시는 프레임을 포함해 모호한 결합에 의한 revision 충돌을 피한다.
        if (paragraphs == null || paragraphs.isEmpty() || paragraphs.size() > 3000
                || paragraphs.stream().anyMatch(p -> p == null)
                || paragraphs.stream().mapToInt(String::length).sum() > MAX_SOURCE_CHARS) {
            throw new NovelProblem("SOURCE_SIZE_INVALID", 413);
        }
        StringBuilder identity = new StringBuilder(key.value()).append('|').append(policy.version());
        for (String paragraph : paragraphs) {
            identity.append('|').append(paragraph.length()).append(':').append(paragraph);
        }
        if (!ruby.isEmpty()) {
            if (ruby.size() != paragraphs.size()) {
                throw new NovelProblem("SOURCE_RUBY_INVALID", 422);
            }
            for (int p = 0; p < ruby.size(); p++) {
                int previousEnd = 0;
                for (SourceEpisode.RubyToken token : ruby.get(p)) {
                    if (token.startOffset() < previousEnd || token.endOffset() <= token.startOffset()
                            || token.endOffset() > paragraphs.get(p).length() || token.text().length() > MAX_UNIT_CHARS
                            || token.reading().length() > 500 || !paragraphs.get(p).substring(token.startOffset(), token.endOffset()).equals(token.text())) {
                        throw new NovelProblem("SOURCE_RUBY_INVALID", 422);
                    }
                    previousEnd = token.endOffset();
                    identity.append('|').append(p).append(':').append(token.startOffset()).append(':').append(token.endOffset())
                            .append(':').append(token.reading().length()).append(':').append(token.reading());
                }
            }
        }
        String revision = hash(identity.toString());
        List<SourceEpisode.Segment> segments = new ArrayList<>();

        // 원문을 substring으로 분리하여 공백, 빈 문단, 대사 닫힘 문자를 모두 보존한다.
        for (int p = 0; p < paragraphs.size(); p++) {
            String paragraph = paragraphs.get(p);
            String paragraphId = revision.substring(0, 16) + "-p" + p;
            int start = 0;
            do {
                int end = boundary(paragraph, start);
                List<SourceEpisode.RubyToken> annotations = ruby.isEmpty() ? List.of() : ruby.get(p);
                for (SourceEpisode.RubyToken token : annotations) {
                    if (token.startOffset() < end && token.endOffset() > end) {
                        end = token.endOffset();
                    }
                }
                String text = paragraph.substring(start, end);
                List<SourceEpisode.RubyToken> tokens = new ArrayList<>();
                for (SourceEpisode.RubyToken token : annotations) {
                    if (token.startOffset() >= start && token.endOffset() <= end) {
                        tokens.add(new SourceEpisode.RubyToken(token.startOffset() - start, token.endOffset() - start,
                                token.text(), token.reading()));
                    }
                }
                segments.add(new SourceEpisode.Segment(paragraphId + "-s" + start, paragraphId,
                        segments.size(), p, start, end, text, text, tokens));
                if (segments.size() > 3000) throw new NovelProblem("SOURCE_SEGMENTS_EXCEEDED", 413);
                start = end;
            } while (start < paragraph.length());
        }
        if (segments.size() > 3000) {
            throw new NovelProblem("SOURCE_SEGMENTS_EXCEEDED", 413);
        }
        return new SourceEpisode(revision, policy.version(), title, previous, next, segments);
    }

    private int boundary(String text, int start) {
        int limit = Math.min(text.length(), start + MAX_UNIT_CHARS);
        if (limit < text.length() && Character.isHighSurrogate(text.charAt(limit - 1))) {
            limit--;
        }
        if (policy == Policy.QUOTE_V2) return quotedBoundary(text, start, limit);
        for (int i = start; i < limit; i++) {
            char ch = text.charAt(i);
            if ("。！？!?\n".indexOf(ch) < 0) {
                continue;
            }
            int end = i + 1;
            while (end < limit && "。！？!?」』）】〕〉》”’\"".indexOf(text.charAt(end)) >= 0) {
                end++;
            }
            return end;
        }
        return limit;
    }

    private int quotedBoundary(String text, int start, int limit) {
        // Reconstruct quote state from this paragraph when a long unit/ruby forced an earlier cut.
        // A closing quote followed by a particle or narration stays with that clause; only adjacent
        // top-level quotes separate turns. This is a bounded heuristic, not speaker identification.
        var closings = new java.util.ArrayDeque<Character>();
        for (int i = 0; i < limit; i++) {
            char ch = text.charAt(i);
            char close = switch (ch) { case '「' -> '」'; case '『' -> '』'; case '“' -> '”'; case '‘' -> '’'; default -> 0; };
            if (close != 0) { closings.push(close); continue; }
            boolean closedQuote = !closings.isEmpty() && closings.peek() == ch;
            if (closedQuote) closings.pop();
            if (i < start || !closings.isEmpty()) continue;
            if (closedQuote) {
                int next = i + 1;
                while (next < text.length() && Character.isWhitespace(text.charAt(next))) next++;
                if (next < text.length() && "「『“‘".indexOf(text.charAt(next)) >= 0) return i + 1;
            }
            if ("。！？!?\n".indexOf(ch) < 0) continue;
            int end = i + 1;
            while (end < limit && "。！？!?）】〕〉》".indexOf(text.charAt(end)) >= 0) end++;
            return end;
        }
        return limit;
    }

    public static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA256_UNAVAILABLE");
        }
    }
}
