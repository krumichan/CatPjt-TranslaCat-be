package jp.co.translacat.novel;

import jp.co.translacat.novel.domain.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/** 独立 authored boundary fixtures; expected Korean translations are never inputs. */
class QuoteBoundaryTest {
    private final EpisodeKey key = new EpisodeKey("syosyetu", "n2604qf", "901");
    private SentenceSegmenter candidate() { return new SentenceSegmenter(SentenceSegmenter.Policy.QUOTE_V2); }

    @Test void completeUtterancesKeepInternalStopsAndSplitAdjacentSpeakers() {
        assertUnits("「まだ開いている。先に入ろう」「私は待つ」「では、あとで」",
                "「まだ開いている。先に入ろう」", "「私は待つ」", "「では、あとで」");
        assertUnits("「ここ？」「そこだよ！」「分かった」", "「ここ？」", "「そこだよ！」", "「分かった」");
    }
    @Test void quotedAttributionNegationAndNestedQuotesStayWithTheirSentence() {
        assertUnits("彼は「終わった。すべて」とは言わなかった。妹は黙った。",
                "彼は「終わった。すべて」とは言わなかった。", "妹は黙った。");
        assertUnits("「『帰る』と言った。覚えている？」と彼女は聞いた。「覚えている」と弟は答えた。",
                "「『帰る』と言った。覚えている？」と彼女は聞いた。", "「覚えている」と弟は答えた。");
        assertUnits("「そう」と言った。『違う』とは言わなかった。", "「そう」と言った。", "『違う』とは言わなかった。");
    }
    @Test void rubyOffsetsWhitespaceSurrogatesAndLongQuotesReconstructExactly() {
        String text = "「旅。明日」と答えた。\n　次へ😀。";
        var tokens = List.of(new SourceEpisode.RubyToken(1, 5, "旅。明日", "たびあす"));
        var source = candidate().segment(key, "題", List.of(text, "\u00a0\ufeff"), null, null, List.of(tokens, List.of()));
        assertThat(source.segments().stream().filter(s -> s.paragraphIndex() == 0).map(SourceEpisode.Segment::plainJa).reduce("", String::concat)).isEqualTo(text);
        assertThat(source.segments().getFirst().rubyTokens()).containsExactly(tokens.getFirst());
        String longQuote = "「" + "😀。".repeat(900) + "終わり」次だ。";
        var longSource = candidate().segment(key, "", List.of(longQuote), null, null);
        assertThat(longSource.segments().stream().map(SourceEpisode.Segment::plainJa).reduce("", String::concat)).isEqualTo(longQuote);
        assertThat(longSource.segments()).allSatisfy(s -> {
            assertThat(s.plainJa().length()).isLessThanOrEqualTo(1500);
            assertThat(Character.isHighSurrogate(s.plainJa().charAt(s.plainJa().length() - 1))).isFalse();
            assertThat(Character.isLowSurrogate(s.plainJa().charAt(0))).isFalse();
        });
    }
    @Test void oldDefaultIdentityAndCacheRemainSeparateFromTheOptInCandidate() {
        String text = "「まだ開いている。先に入ろう」「私は待つ」";
        var original = new SentenceSegmenter().segment(key, "", List.of(text), null, null);
        var candidate = candidate().segment(key, "", List.of(text), null, null);
        assertThat(original.segmentationVersion()).isEqualTo("ja-boundaries-v1");
        assertThat(original.revision()).isEqualTo(SentenceSegmenter.hash(key.value() + "|ja-boundaries-v1|" + text.length() + ":" + text));
        assertThat(original.segments().stream().map(SourceEpisode.Segment::plainJa)).containsExactly("「まだ開いている。", "先に入ろう」「私は待つ」");
        assertThat(candidate.segmentationVersion()).isEqualTo("ja-boundaries-v2-quotes");
        assertThat(candidate.revision()).isNotEqualTo(original.revision());
        var oldOptions = TranslationOptions.baseline("test|legacy|none|8192|standard");
        var newOptions = new TranslationOptions(0, 2, TranslationOptions.Strategy.LEGACY, TranslationOptions.Context.CURRENT,
                8192, oldOptions.profileIdentity(), oldOptions.responseShape(), SentenceSegmenter.Policy.QUOTE_V2);
        assertThat(oldOptions.fingerprint()).isNotEqualTo(newOptions.fingerprint());
        var policy = new TranslationPolicy();
        assertThat(policy.cacheKey(key, original, 1, "test", "empty-v1", oldOptions))
                .isNotEqualTo(policy.cacheKey(key, candidate, 1, "test", "empty-v1", newOptions));
        assertThatThrownBy(() -> new TranslationPlanner().plan(original, newOptions)).hasMessage("SOURCE_SEGMENTATION_POLICY_MISMATCH");
        assertThatThrownBy(() -> new TranslationPlanner().plan(candidate, oldOptions)).hasMessage("SOURCE_SEGMENTATION_POLICY_MISMATCH");
        assertThat(new TranslationPlanner().plan(candidate, newOptions).segmentationVersion()).isEqualTo(candidate.segmentationVersion());
    }
    @Test void unmatchedAndMultilineQuotesPreserveEverySourceCharacterWithinSafetyBounds() {
        String text = "　「閉じていない。\nそれでも続きを消さない。" + "文。".repeat(900);
        var source = candidate().segment(key, "", List.of(text, "最後。"), null, null);
        assertThat(source.segments().stream().filter(s -> s.paragraphIndex() == 0).map(SourceEpisode.Segment::plainJa).reduce("", String::concat)).isEqualTo(text);
        assertThat(source.segments()).allSatisfy(s -> assertThat(s.plainJa().length()).isLessThanOrEqualTo(1500));
        assertThat(source.segments().getLast().plainJa()).isEqualTo("最後。");
    }
    private void assertUnits(String text, String... expected) {
        var source = candidate().segment(key, "", List.of(text), null, null);
        assertThat(source.segments().stream().map(SourceEpisode.Segment::plainJa)).containsExactly(expected);
        assertThat(String.join("", expected)).isEqualTo(text);
    }
}
