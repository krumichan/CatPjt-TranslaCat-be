package jp.co.translacat.domain.languagelearning.listening.attempt.service;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.listening.audio.model.ListeningAudioObject;
import jp.co.translacat.domain.languagelearning.listening.audio.port.ListeningAudioStoragePort;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningItemRepository;
import jp.co.translacat.domain.languagelearning.listening.response.entity.ListeningTaskResponse;
import jp.co.translacat.domain.languagelearning.listening.response.repository.ListeningTaskResponseRepository;
import jp.co.translacat.domain.languagelearning.listening.service.ListeningViewMapper;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListeningAttemptQueryServiceUserAudioTest {

    @Mock
    private ListeningItemAttemptRepository attemptRepository;
    @Mock
    private ListeningItemRepository itemRepository;
    @Mock
    private ListeningTaskResponseRepository responseRepository;
    @Mock
    private ListeningAudioStoragePort storagePort;
    @Mock
    private ListeningViewMapper viewMapper;
    @Mock
    private LanguageLearningJsonCodec jsonCodec;
    @Mock
    private ListeningTaskResponse response;

    private ListeningAttemptQueryService service;

    @BeforeEach
    void setUp() {
        service = new ListeningAttemptQueryService(
                attemptRepository,
                itemRepository,
                responseRepository,
                storagePort,
                viewMapper,
                jsonCodec
        );
    }

    @Test
    void loadsOwnedRetainedUserAudio() {
        when(responseRepository.findByIdAndAttemptSessionUserId(10L, 7L))
                .thenReturn(Optional.of(response));
        when(response.getUserAudioObjectKey()).thenReturn("users/7/repeat.webm");
        when(response.getAudioContentType()).thenReturn("audio/webm");
        when(response.getAudioRetentionUntil())
                .thenReturn(LocalDateTime.now().plusDays(1));
        ListeningAudioObject stored = new ListeningAudioObject(
                "users/7/repeat.webm",
                new byte[]{1, 2, 3},
                "audio/webm"
        );
        when(storagePort.load("users/7/repeat.webm", "audio/webm"))
                .thenReturn(stored);

        assertThat(service.userAudio(7L, 10L)).isSameAs(stored);
    }

    @Test
    void rejectsExpiredUserAudio() {
        when(responseRepository.findByIdAndAttemptSessionUserId(10L, 7L))
                .thenReturn(Optional.of(response));
        when(response.getUserAudioObjectKey()).thenReturn("users/7/repeat.webm");
        when(response.getAudioRetentionUntil())
                .thenReturn(LocalDateTime.now().minusSeconds(1));

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> service.userAudio(7L, 10L)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(LanguageLearningErrorCode.LISTENING_AUDIO_INVALID);
    }
}
