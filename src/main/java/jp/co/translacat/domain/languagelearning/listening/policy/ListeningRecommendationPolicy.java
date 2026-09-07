package jp.co.translacat.domain.languagelearning.listening.policy;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningProfileMetric;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ListeningRecommendationPolicy {

    public static final String VERSION = "listening-recommendation";

    private static final Map<ListeningProfileMetric, Target> RULES = Map.of(
            ListeningProfileMetric.LISTENING_RECOGNITION,
            new Target("LISTENING", "DICTATION"),
            ListeningProfileMetric.VOCABULARY,
            new Target("LISTENING", "DICTATION"),
            ListeningProfileMetric.ORTHOGRAPHY,
            new Target("WRITING", "DAILY_WRITING"),
            ListeningProfileMetric.MEANING,
            new Target("LISTENING", "INTERPRETATION"),
            ListeningProfileMetric.ORIGIN_NATURALNESS,
            new Target("WRITING", "DAILY_WRITING"),
            ListeningProfileMetric.PRONUNCIATION,
            new Target("LISTENING", "REPEAT_AFTER_AUDIO"),
            ListeningProfileMetric.FLUENCY,
            new Target("LISTENING", "REPEAT_AFTER_AUDIO"),
            ListeningProfileMetric.SPOKEN_EXPRESSION,
            new Target("SPEAKING", "FREE_CONVERSATION"),
            ListeningProfileMetric.INTERACTION,
            new Target("SPEAKING", "ROLE_PLAY"),
            ListeningProfileMetric.WRITTEN_EXPRESSION,
            new Target("WRITING", "DAILY_WRITING")
    );

    public Target target(ListeningProfileMetric metric) {
        return RULES.get(metric);
    }

    public record Target(
            String activity,
            String task
    ) {
    }
}
