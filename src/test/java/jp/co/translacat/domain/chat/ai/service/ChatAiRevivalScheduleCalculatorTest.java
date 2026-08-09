package jp.co.translacat.domain.chat.ai.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ChatAiRevivalScheduleCalculatorTest {

    private final ChatAiRevivalScheduleCalculator calculator =
            new ChatAiRevivalScheduleCalculator("Asia/Tokyo");

    @Test
    void firstRevivalIsNotBeforeTwentyFourHoursAndStaysInAllowedWindow() {
        LocalDateTime humanAt = LocalDateTime.of(2026, 8, 9, 9, 0);

        LocalDateTime scheduled = calculator.scheduleAfterHours(
                10L,
                1L,
                1,
                humanAt,
                24,
                LocalTime.of(10, 0),
                LocalTime.of(22, 0)
        );

        assertThat(scheduled).isAfterOrEqualTo(humanAt.plusHours(24));
        assertThat(scheduled.toLocalTime())
                .isAfterOrEqualTo(LocalTime.of(10, 0));
        assertThat(scheduled.toLocalTime())
                .isBefore(LocalTime.of(22, 0));
    }

    @Test
    void thresholdAfterAllowedWindowMovesToNextAllowedDay() {
        LocalDateTime baseAt = LocalDateTime.of(2026, 8, 9, 23, 0);

        LocalDateTime scheduled = calculator.scheduleAfterHours(
                10L,
                1L,
                1,
                baseAt,
                24,
                LocalTime.of(10, 0),
                LocalTime.of(22, 0)
        );

        assertThat(scheduled.toLocalDate())
                .isEqualTo(baseAt.toLocalDate().plusDays(2));
        assertThat(scheduled.toLocalTime())
                .isAfterOrEqualTo(LocalTime.of(10, 0));
        assertThat(scheduled.toLocalTime())
                .isBefore(LocalTime.of(22, 0));
    }

    @Test
    void sameInputsProduceStableDistributedTimeForRetrySafety() {
        LocalDateTime baseAt = LocalDateTime.of(2026, 8, 9, 12, 0);

        LocalDateTime first = calculator.scheduleAfterHours(
                77L, 3L, 2, baseAt, 72,
                LocalTime.of(10, 0), LocalTime.of(22, 0)
        );
        LocalDateTime second = calculator.scheduleAfterHours(
                77L, 3L, 2, baseAt, 72,
                LocalTime.of(10, 0), LocalTime.of(22, 0)
        );

        assertThat(second).isEqualTo(first);
    }
}
