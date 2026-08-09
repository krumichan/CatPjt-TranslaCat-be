package jp.co.translacat.domain.chat.ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

@Component
public class ChatAiRevivalScheduleCalculator {

    private final ZoneId zoneId;

    public ChatAiRevivalScheduleCalculator(
            @Value("${translacat.batch.zone:Asia/Tokyo}") String zone
    ) {
        this.zoneId = ZoneId.of(zone);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(zoneId);
    }

    public LocalDateTime scheduleAfterHours(
            Long roomId,
            long cycleVersion,
            int stageSeed,
            LocalDateTime baseAt,
            int delayHours,
            LocalTime allowedStart,
            LocalTime allowedEnd
    ) {
        if (baseAt == null) {
            throw new IllegalArgumentException("REVIVAL 기준 시각은 필수입니다.");
        }
        return distributeAtOrAfter(
                roomId,
                cycleVersion,
                stageSeed,
                baseAt.plusHours(delayHours),
                allowedStart,
                allowedEnd
        );
    }

    public LocalDateTime scheduleAtOrAfter(
            Long roomId,
            long cycleVersion,
            int stageSeed,
            LocalDateTime earliest,
            LocalTime allowedStart,
            LocalTime allowedEnd
    ) {
        return distributeAtOrAfter(
                roomId,
                cycleVersion,
                stageSeed,
                earliest,
                allowedStart,
                allowedEnd
        );
    }

    public boolean isWithinAllowedWindow(
            LocalDateTime value,
            LocalTime allowedStart,
            LocalTime allowedEnd
    ) {
        validateWindow(allowedStart, allowedEnd);
        if (value == null) {
            return false;
        }
        LocalTime time = value.toLocalTime();
        return !time.isBefore(allowedStart) && time.isBefore(allowedEnd);
    }

    public LocalDateTime scheduleRetry(
            LocalDateTime failedAt,
            int retryDelayMinutes,
            LocalTime allowedStart,
            LocalTime allowedEnd
    ) {
        validateWindow(allowedStart, allowedEnd);
        LocalDateTime earliest = failedAt.plusMinutes(
                Math.max(1, retryDelayMinutes)
        );
        LocalTime time = earliest.toLocalTime();
        if (time.isBefore(allowedStart)) {
            return LocalDateTime.of(
                    earliest.toLocalDate(),
                    allowedStart
            );
        }
        if (!time.isBefore(allowedEnd)) {
            return LocalDateTime.of(
                    earliest.toLocalDate().plusDays(1),
                    allowedStart
            );
        }
        return earliest;
    }

    LocalDateTime distributeAtOrAfter(
            Long roomId,
            long cycleVersion,
            int stageSeed,
            LocalDateTime earliest,
            LocalTime allowedStart,
            LocalTime allowedEnd
    ) {
        validateWindow(allowedStart, allowedEnd);

        LocalDate targetDate = earliest.toLocalDate();
        LocalDateTime windowStart = LocalDateTime.of(
                targetDate,
                allowedStart
        );
        LocalDateTime windowEnd = LocalDateTime.of(
                targetDate,
                allowedEnd
        );

        if (!earliest.isBefore(windowEnd)) {
            targetDate = targetDate.plusDays(1);
            windowStart = LocalDateTime.of(targetDate, allowedStart);
            windowEnd = LocalDateTime.of(targetDate, allowedEnd);
        } else if (earliest.isAfter(windowStart)) {
            windowStart = earliest;
        }

        long availableSeconds = Duration.between(
                windowStart,
                windowEnd
        ).getSeconds();
        if (availableSeconds <= 1L) {
            return windowStart;
        }

        long seed = Objects.hash(
                roomId,
                cycleVersion,
                stageSeed,
                targetDate.toEpochDay()
        );
        long mixed = mix64(seed);
        long offset = Math.floorMod(mixed, availableSeconds);
        return windowStart.plusSeconds(offset);
    }

    private void validateWindow(
            LocalTime allowedStart,
            LocalTime allowedEnd
    ) {
        if (allowedStart == null
                || allowedEnd == null
                || !allowedStart.isBefore(allowedEnd)) {
            throw new IllegalArgumentException(
                    "REVIVAL 허용 시작 시간은 종료 시간보다 빨라야 합니다."
            );
        }
    }

    private long mix64(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
