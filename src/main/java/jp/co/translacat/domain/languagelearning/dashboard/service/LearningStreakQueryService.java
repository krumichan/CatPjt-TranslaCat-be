package jp.co.translacat.domain.languagelearning.dashboard.service;

import jp.co.translacat.domain.languagelearning.common.enums.DailySetStatus;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.StreakResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingSessionStatus;
import jp.co.translacat.domain.languagelearning.speaking.session.repository.SpeakingSessionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningStreakQueryService {

    private final DailyWritingSetRepository dailySetRepository;
    private final SpeakingSessionRepository speakingSessionRepository;

    public StreakResponseDto get(Long userId, LocalDate today) {
        Set<LocalDate> completedDates = new LinkedHashSet<>();
        dailySetRepository
                .findAllByUserIdAndStatusOrderByLearningDateDesc(
                        userId,
                        DailySetStatus.COMPLETED
                )
                .forEach(set -> completedDates.add(set.getLearningDate()));
        speakingSessionRepository
                .findAllByUserIdAndLearningDateBetweenOrderByLearningDateDescStartedAtDesc(
                        userId,
                        today.minusDays(3650),
                        today
                )
                .stream()
                .filter(session -> !session.isActive())
                .filter(session -> session.getStatus() != SpeakingSessionStatus.EXPIRED)
                .forEach(session -> completedDates.add(session.getLearningDate()));

        int current = consecutiveFrom(completedDates, today);
        List<LocalDate> sorted = completedDates.stream().sorted().toList();
        int longest = 0;
        int running = 0;
        LocalDate previous = null;
        for (LocalDate date : sorted) {
            running = previous != null && date.equals(previous.plusDays(1))
                    ? running + 1
                    : 1;
            longest = Math.max(longest, running);
            previous = date;
        }
        LocalDate last = sorted.isEmpty() ? null : sorted.get(sorted.size() - 1);
        return new StreakResponseDto(current, longest, last);
    }

    private int consecutiveFrom(Set<LocalDate> dates, LocalDate today) {
        LocalDate cursor = dates.contains(today) ? today : today.minusDays(1);
        int result = 0;
        while (dates.contains(cursor)) {
            result++;
            cursor = cursor.minusDays(1);
        }
        return result;
    }
}
