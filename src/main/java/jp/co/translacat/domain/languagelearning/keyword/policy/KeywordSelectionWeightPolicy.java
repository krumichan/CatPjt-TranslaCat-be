package jp.co.translacat.domain.languagelearning.keyword.policy;

import jp.co.translacat.domain.languagelearning.keyword.entity.KeywordMastery;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Component
public class KeywordSelectionWeightPolicy {

    public double calculateRawWeight(
            LocalDateTime availableFrom,
            KeywordMastery mastery,
            LocalDate learningDate
    ) {
        double rampWeight = calculateRampWeight(
                availableFrom,
                learningDate
        );
        double masteryWeight = mastery == null
                ? 1.10
                : 1.25 - mastery.getScore() / 250.0;
        double recencyWeight = calculateRecencyWeight(
                mastery,
                learningDate
        );

        return Math.max(
                0.01,
                rampWeight * masteryWeight * recencyWeight
        );
    }

    public double normalizeSelectionWeight(
            double rawWeight,
            double maxRawWeight
    ) {
        double normalized = rawWeight / Math.max(maxRawWeight, 0.0001);
        double bounded = Math.max(0.01, Math.min(1.0, normalized));

        return round(bounded);
    }

    public String normalizeCanonicalKey(
            String canonicalKey,
            String keywordText
    ) {
        if (canonicalKey == null || canonicalKey.isBlank()) {
            return keywordText.toLowerCase(Locale.ROOT);
        }

        return canonicalKey;
    }

    double calculateRampWeight(
            LocalDateTime availableFrom,
            LocalDate learningDate
    ) {
        if (availableFrom == null) {
            return 1.0;
        }

        long activeDay = ChronoUnit.DAYS.between(
                availableFrom.toLocalDate(),
                learningDate
        ) + 1;

        if (activeDay <= 1) {
            return 0.25;
        }
        if (activeDay <= 3) {
            return 0.50;
        }
        if (activeDay <= 6) {
            return 0.75;
        }

        return 1.0;
    }

    double calculateRecencyWeight(
            KeywordMastery mastery,
            LocalDate learningDate
    ) {
        if (mastery == null || mastery.getLastSelectedDate() == null) {
            return 1.20;
        }

        long elapsedDays = ChronoUnit.DAYS.between(
                mastery.getLastSelectedDate(),
                learningDate
        );

        if (elapsedDays <= 1) {
            return 0.55;
        }
        if (elapsedDays <= 3) {
            return 0.75;
        }
        if (elapsedDays <= 6) {
            return 1.00;
        }

        return 1.20;
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
