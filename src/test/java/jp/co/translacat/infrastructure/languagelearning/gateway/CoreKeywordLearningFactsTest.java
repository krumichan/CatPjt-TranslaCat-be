package jp.co.translacat.infrastructure.languagelearning.gateway;

import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.speaking.session.repository.SpeakingSessionRepository;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoreKeywordLearningFactsTest {
    @Test void writingHistoryMeansStartedWithoutCheckingSpeakingAgain() {
        var writing = mock(DailyWritingSetRepository.class);
        var speaking = mock(SpeakingSessionRepository.class);
        when(writing.existsByUserId(123L)).thenReturn(true);
        assertTrue(new CoreKeywordLearningFacts(writing, speaking).hasStartedLearning(123L));
        verifyNoInteractions(speaking);
    }
    @Test void speakingHistoryIsCheckedWhenWritingHasNotStarted() {
        var writing = mock(DailyWritingSetRepository.class);
        var speaking = mock(SpeakingSessionRepository.class);
        when(speaking.existsByUserId(123L)).thenReturn(true);
        assertTrue(new CoreKeywordLearningFacts(writing, speaking).hasStartedLearning(123L));
        verify(writing).existsByUserId(123L);
    }
    @Test void noWritingOrSpeakingKeepsImmediateInitialApplication() {
        var writing = mock(DailyWritingSetRepository.class);
        var speaking = mock(SpeakingSessionRepository.class);
        assertFalse(new CoreKeywordLearningFacts(writing, speaking).hasStartedLearning(123L));
        verify(writing).existsByUserId(123L);
        verify(speaking).existsByUserId(123L);
    }
}
