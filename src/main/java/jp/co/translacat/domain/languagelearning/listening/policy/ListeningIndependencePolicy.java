package jp.co.translacat.domain.languagelearning.listening.policy;

import org.springframework.stereotype.Component;

@Component
public class ListeningIndependencePolicy {

    public static final String VERSION = "listening-independence";

    public int score(
            long normalPlaybackCount,
            long slowPlaybackCount
    ) {
        long replayCount = Math.max(normalPlaybackCount - 1, 0);
        long normalPenalty = Math.min(replayCount, 4) * 5;
        long slowPenalty = Math.min(
                Math.max(slowPlaybackCount, 0),
                2
        ) * 10;
        return (int) Math.max(
                60,
                100 - normalPenalty - slowPenalty
        );
    }

    public double adjustedOverall(
            double contentOverall,
            double independence
    ) {
        return Math.round(
                contentOverall * 0.85 + independence * 0.15
        );
    }
}
