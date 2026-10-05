package jp.co.translacat.novel.domain;

import java.util.List;

public record SourceEpisode(String revision, String segmentationVersion, String title,
                            String prevEpisodeId, String nextEpisodeId, List<Segment> segments) {
    public SourceEpisode {
        segments = List.copyOf(segments);
    }

    public record RubyToken(int startOffset, int endOffset, String text, String reading) {}
    public record Segment(String id, String paragraphId, int order, int paragraphIndex,
                          int startOffset, int endOffset, String rawJa, String plainJa,
                          List<RubyToken> rubyTokens) {
        public Segment {
            rubyTokens = rubyTokens == null ? List.of() : List.copyOf(rubyTokens);
        }
        public Segment(String id, String paragraphId, int order, int paragraphIndex,
                       int startOffset, int endOffset, String rawJa, String plainJa) {
            this(id, paragraphId, order, paragraphIndex, startOffset, endOffset, rawJa, plainJa, List.of());
        }
    }
}
