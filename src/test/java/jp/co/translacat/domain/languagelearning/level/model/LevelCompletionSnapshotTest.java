package jp.co.translacat.domain.languagelearning.level.model;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class LevelCompletionSnapshotTest {
    private final LocalDateTime at = LocalDateTime.parse("2026-09-25T01:00:00");

    private LevelCompletionSnapshot value(int score, String band) {
        return new LevelCompletionSnapshot(123L, 1L, "5f55806e-767d-4c0d-82fd-66e44ee094f2",
                LevelTestSessionType.INITIAL, score, band, at.toLocalDate(), at.minusMinutes(10), at);
    }

    @Test
    public void stableHashIncludesScoreAndIsDeterministic() {
        assertEquals(value(65, "INTERMEDIATE").contentHash(), value(65, "INTERMEDIATE").contentHash());
        assertNotEquals(value(65, "INTERMEDIATE").contentHash(), value(66, "INTERMEDIATE").contentHash());
    }

    @Test
    public void inconsistentScoreBandIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> value(65, "ADVANCED"));
        assertThrows(IllegalArgumentException.class, () -> value(101, "ADVANCED"));
    }

    @Test
    public void allThresholdsMatchOriginalContract() {
        int[] scores = {0, 39, 40, 54, 55, 69, 70, 84, 85, 100};
        String[] bands = {
                "FOUNDATION",
                "FOUNDATION",
                "BASIC",
                "BASIC",
                "INTERMEDIATE",
                "INTERMEDIATE",
                "UPPER_INTERMEDIATE",
                "UPPER_INTERMEDIATE",
                "ADVANCED",
                "ADVANCED"
        };
        for (int i = 0; i < scores.length; i++) assertNotNull(value(scores[i], bands[i]));
    }
}
