package jp.co.translacat.domain.languagelearning.level.pool.audio.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestReferenceAudioDto;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;
import jp.co.translacat.domain.languagelearning.level.pool.audio.model.LevelTestReferenceAudioUpload;
import jp.co.translacat.domain.languagelearning.level.pool.audio.port.LevelTestReferenceAudioUploadPort;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LevelTestReferenceAudioUploadServiceTest {

    @Test
    void preparesPresignedUploadOnlyForReferenceAudioItems() {
        LevelTestReferenceAudioUploadPort port = mock(
                LevelTestReferenceAudioUploadPort.class
        );
        @SuppressWarnings("unchecked")
        ObjectProvider<LevelTestReferenceAudioUploadPort> provider =
                mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(port);
        when(port.prepare(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("audio/wav"),
                org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(300))
        )).thenAnswer(invocation -> new LevelTestReferenceAudioUpload(
                "https://example.invalid/presigned",
                invocation.getArgument(0),
                "audio/wav"
        ));

        LevelTestReferenceAudioUploadService service =
                new LevelTestReferenceAudioUploadService(provider, 300);

        var listening = service.prepare(
                "ja",
                "level:pool:test",
                LevelTestDomain.LISTENING,
                LevelTestItemType.LISTENING_GIST_CHOICE
        );
        var reading = service.prepare(
                "ja",
                "level:pool:test-reading",
                LevelTestDomain.READING,
                LevelTestItemType.READING_GIST
        );

        assertThat(listening).isNotNull();
        assertThat(listening.objectKey())
                .startsWith("language-learning/level-test/reference/ja/")
                .endsWith(".wav");
        assertThat(listening.contentType()).isEqualTo("audio/wav");
        assertThat(reading).isNull();
    }

    @Test
    void verifiesUploadedObjectThroughStoragePort() {
        LevelTestReferenceAudioUploadPort port = mock(
                LevelTestReferenceAudioUploadPort.class
        );
        @SuppressWarnings("unchecked")
        ObjectProvider<LevelTestReferenceAudioUploadPort> provider =
                mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(port);
        when(port.exists("audio/test.wav")).thenReturn(true);

        LevelTestReferenceAudioUploadService service =
                new LevelTestReferenceAudioUploadService(provider, 300);
        service.verify(new LevelTestReferenceAudioDto(
                "audio/test.wav",
                "audio/wav",
                1200,
                "0".repeat(64)
        ));

        verify(port).exists("audio/test.wav");
    }
}
