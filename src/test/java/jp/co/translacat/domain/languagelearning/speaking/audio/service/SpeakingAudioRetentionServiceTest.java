package jp.co.translacat.domain.languagelearning.speaking.audio.service;

import jp.co.translacat.domain.languagelearning.speaking.audio.port.SpeakingAudioStoragePort;
import jp.co.translacat.domain.languagelearning.speaking.session.repository.SpeakingSessionRepository;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.speaking.turn.repository.SpeakingTurnRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpeakingAudioRetentionServiceTest {

    @Mock
    private SpeakingTurnRepository turnRepository;

    @Mock
    private SpeakingSessionRepository sessionRepository;

    @Mock
    private SpeakingAudioStoragePort audioStoragePort;

    @Mock
    private SpeakingTurn turn;

    private SpeakingAudioRetentionService service;

    @BeforeEach
    void setUp() {
        service = new SpeakingAudioRetentionService(
                turnRepository,
                sessionRepository,
                audioStoragePort
        );
        when(turnRepository
                .findAllByAssistantAudioRetentionUntilBeforeAndAssistantAudioObjectKeyIsNotNull(
                        org.mockito.ArgumentMatchers.any()
                ))
                .thenReturn(List.of());
        when(sessionRepository
                .findAllByOpeningAssistantAudioRetentionUntilBeforeAndOpeningAssistantAudioObjectKeyIsNotNull(
                        org.mockito.ArgumentMatchers.any()
                ))
                .thenReturn(List.of());
    }

    @Test
    void clearsObjectKeyOnlyAfterStorageDeletionSucceeds() {
        when(turnRepository
                .findAllByUserAudioRetentionUntilBeforeAndUserAudioObjectKeyIsNotNull(
                        org.mockito.ArgumentMatchers.any()
                ))
                .thenReturn(List.of(turn));
        when(turn.getUserAudioObjectKey()).thenReturn("user-audio-key");

        service.deleteExpiredAudio();

        verify(audioStoragePort).delete("user-audio-key");
        verify(turn).clearUserAudio();
    }

    @Test
    void keepsObjectKeyWhenStorageDeletionFailsSoNextJobCanRetry() {
        when(turnRepository
                .findAllByUserAudioRetentionUntilBeforeAndUserAudioObjectKeyIsNotNull(
                        org.mockito.ArgumentMatchers.any()
                ))
                .thenReturn(List.of(turn));
        when(turn.getUserAudioObjectKey()).thenReturn("user-audio-key");
        doThrow(new RuntimeException("storage unavailable"))
                .when(audioStoragePort)
                .delete("user-audio-key");

        service.deleteExpiredAudio();

        verify(turn, never()).clearUserAudio();
    }
}
