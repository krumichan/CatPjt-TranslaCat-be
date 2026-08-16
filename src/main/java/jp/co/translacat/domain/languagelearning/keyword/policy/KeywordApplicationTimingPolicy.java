package jp.co.translacat.domain.languagelearning.keyword.policy;

import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.speaking.session.repository.SpeakingSessionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class KeywordApplicationTimingPolicy {

    private final DailyWritingSetRepository dailyWritingSetRepository;
    private final SpeakingSessionRepository speakingSessionRepository;

    public LocalDate resolveEffectiveDate(
            Long userId,
            LocalDate today
    ) {
        return hasStartedLearning(userId)
                ? today.plusDays(1)
                : today;
    }

    public boolean hasStartedLearning(Long userId) {
        return dailyWritingSetRepository.existsByUserId(userId)
                || speakingSessionRepository.existsByUserId(userId);
    }
}
