package jp.co.translacat.domain.languagelearning.keyword.policy;

import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.speaking.session.repository.SpeakingSessionRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeywordApplicationTimingPolicyTest {

    private static final Long USER_ID = 10L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 16);

    @Mock
    private DailyWritingSetRepository dailyWritingSetRepository;

    @Mock
    private SpeakingSessionRepository speakingSessionRepository;

    @InjectMocks
    private KeywordApplicationTimingPolicy policy;

    @Test
    void appliesImmediatelyBeforeAnyWritingOrSpeakingStarts() {
        when(dailyWritingSetRepository.existsByUserId(USER_ID))
                .thenReturn(false);
        when(speakingSessionRepository.existsByUserId(USER_ID))
                .thenReturn(false);

        assertThat(policy.resolveEffectiveDate(USER_ID, TODAY))
                .isEqualTo(TODAY);
    }

    @Test
    void appliesNextDayAfterDailyWritingSetIsCreated() {
        when(dailyWritingSetRepository.existsByUserId(USER_ID))
                .thenReturn(true);

        assertThat(policy.resolveEffectiveDate(USER_ID, TODAY))
                .isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void appliesNextDayAfterSpeakingSessionIsCreated() {
        when(dailyWritingSetRepository.existsByUserId(USER_ID))
                .thenReturn(false);
        when(speakingSessionRepository.existsByUserId(USER_ID))
                .thenReturn(true);

        assertThat(policy.resolveEffectiveDate(USER_ID, TODAY))
                .isEqualTo(TODAY.plusDays(1));
    }
}
