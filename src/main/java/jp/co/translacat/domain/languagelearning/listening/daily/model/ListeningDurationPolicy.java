package jp.co.translacat.domain.languagelearning.listening.daily.model;

import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningDifficulty;

/** NORMAL waveform requirement. Model estimates are not publication evidence. */
public final class ListeningDurationPolicy {
    public static final String VERSION = "listening-audio-duration-v1";

    private ListeningDurationPolicy() { }

    public static AiListeningContract.DurationDemand effective(
            ListeningDifficulty difficulty, Double requestedMin, Double requestedMax
    ) {
        double minimum = switch (difficulty) {
            case EASY -> 5.0;
            case MY_LEVEL -> 8.0;
            case CHALLENGE -> 15.0;
        };
        double maximum = switch (difficulty) {
            case EASY -> 12.0;
            case MY_LEVEL -> 20.0;
            case CHALLENGE -> 30.0;
        };
        minimum = Math.max(minimum, requestedMin == null ? minimum : requestedMin);
        maximum = Math.min(maximum, requestedMax == null ? maximum : requestedMax);
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) {
            throw new IllegalArgumentException("LISTENING_DURATION_DEMAND_INVALID");
        }
        return new AiListeningContract.DurationDemand(minimum, maximum, VERSION, "NORMAL");
    }

    public static AiListeningContract.GeneratedItem withDuration(
            AiListeningContract.GeneratedItem item,
            AiListeningContract.DurationDemand demand, int correctionCount
    ) {
        return new AiListeningContract.GeneratedItem(item.itemIndex(), item.sourceText(),
                item.normalizedSourceText(), item.referenceMeanings(), item.keyMeaningUnits(),
                item.targetKeywords(), item.estimatedAudioSeconds(), item.contentHash(),
                item.similarityKey(), item.safety(), item.languageComplexityBand(),
                item.diversityMetadata(), item.question(), item.options(), item.correctOptionKey(),
                item.comprehensionFocus(), item.summaryKeyPoints(), demand, correctionCount);
    }
}
