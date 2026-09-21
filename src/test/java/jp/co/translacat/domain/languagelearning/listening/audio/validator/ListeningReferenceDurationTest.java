package jp.co.translacat.domain.languagelearning.listening.audio.validator;

import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.daily.model.ListeningDurationPolicy;
import jp.co.translacat.domain.languagelearning.listening.daily.model.ListeningReferenceDurationException;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningDifficulty;
import org.junit.jupiter.api.Test;
import java.io.*;
import javax.sound.sampled.*;
import static org.assertj.core.api.Assertions.*;

class ListeningReferenceDurationTest {
    private final ListeningAudioValidator validator = new ListeningAudioValidator();

    @Test
    void actualFramesControlEveryPolicyRangeInclusiveBoundaries() throws Exception {
        for (var difficulty : ListeningDifficulty.values()) {
            var demand = ListeningDurationPolicy.effective(difficulty, 1.0, 30.0);
            for (double seconds : new double[]{demand.minSeconds(), demand.maxSeconds()}) {
                assertThatCode(() -> validator.validateReferenceFrames(wav(seconds), audio(seconds), demand))
                        .doesNotThrowAnyException();
            }
            double shortSeconds = demand.minSeconds() - 0.001;
            assertThatThrownBy(() -> validator.validateReferenceFrames(wav(shortSeconds), audio(shortSeconds), demand))
                    .isInstanceOf(ListeningReferenceDurationException.class).hasMessage("AUDIO_TOO_SHORT");
            double longSeconds = demand.maxSeconds() + 0.001;
            assertThatThrownBy(() -> validator.validateReferenceFrames(wav(longSeconds), audio(longSeconds), demand))
                    .isInstanceOf(ListeningReferenceDurationException.class).hasMessage("AUDIO_TOO_LONG");
        }
    }

    @Test
    void decodeAndMetadataMismatchAreRejectedRatherThanTrustingDuration() throws Exception {
        var demand = ListeningDurationPolicy.effective(ListeningDifficulty.MY_LEVEL, 1.0, 30.0);
        assertThatThrownBy(() -> validator.validateReferenceFrames(wav(4), audio(9), demand))
                .hasMessage("LISTENING_REFERENCE_AUDIO_DURATION_METADATA_MISMATCH");
        assertThatThrownBy(() -> validator.validateReferenceFrames(new byte[]{1, 2, 3}, audio(9), demand))
                .hasMessage("LISTENING_REFERENCE_AUDIO_DECODE_FAILED");
        byte[] complete = wav(9);
        byte[] truncated = java.util.Arrays.copyOf(complete, complete.length - 8);
        assertThatThrownBy(() -> validator.validateReferenceFrames(truncated, audio(9), demand))
                .hasMessage("LISTENING_REFERENCE_AUDIO_INCOMPLETE");
    }

    @Test
    void effectiveRangeUsesIntersectionAndRejectsEmptyRange() {
        var demand = ListeningDurationPolicy.effective(ListeningDifficulty.MY_LEVEL, 10.0, 25.0);
        assertThat(demand.minSeconds()).isEqualTo(10);
        assertThat(demand.maxSeconds()).isEqualTo(20);
        assertThatThrownBy(() -> ListeningDurationPolicy.effective(ListeningDifficulty.EASY, 15.0, 30.0))
                .hasMessage("LISTENING_DURATION_DEMAND_INVALID");
    }

    private static AiListeningContract.Audio audio(double seconds) {
        return new AiListeningContract.Audio("audio", (int) Math.round(seconds * 1000), "audio/wav",
                16000, 1, null, "hash", "checksum", "key", "tts");
    }

    private static byte[] wav(double seconds) throws IOException {
        int frames = (int) Math.round(seconds * 16000);
        var format = new AudioFormat(16000, 16, 1, true, false);
        var bytes = new ByteArrayOutputStream();
        try (var stream = new AudioInputStream(new ByteArrayInputStream(new byte[frames * 2]), format, frames)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, bytes);
        }
        return bytes.toByteArray();
    }
}
